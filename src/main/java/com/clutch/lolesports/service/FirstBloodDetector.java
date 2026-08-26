package com.clutch.lolesports.service;

import com.clutch.coupon.contract.trigger.CouponMatchTrigger;
import com.clutch.coupon.contract.trigger.CouponTriggerPort;
import com.clutch.lolesports.dto.external.WindowResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 첫 킬(퍼스트 블러드) 감지기.
 *
 * <h3>판정 방식</h3>
 * 팀 프레임의 {@code totalKills} 합이 0 에서 1 이상으로 넘어가는 순간을 첫 킬로 본다.
 * 펜타킬과 달리 시간창이 필요 없다 — "짧은 시간에 몇 번"이 아니라 "경기 통틀어 처음"
 * 이라는 단발 조건이라 프레임 간격이 벌어져도 판정이 흔들리지 않는다.
 *
 * <h3>왜 델타가 아니라 절대값을 보나</h3>
 * 첫 킬은 0 → N 이라는 상태 전이다. 증가분으로 보면 폴링이 밀려 첫 킬과 두 번째 킬이
 * 한 프레임에 뭉쳐 들어와도 여전히 "0 이었다가 늘었다"는 사실은 변하지 않는다.
 * 그래서 뭉침에 강하고, {@link PentakillDetector} 처럼 프레임 간격을 신뢰 구간으로
 * 따로 검사할 필요가 없다.
 *
 * <h3>중도 합류 처리</h3>
 * 폴링이 경기 도중 시작되면 첫 관측부터 이미 킬이 쌓여 있다. 이때는 첫 킬이 이미
 * 지나간 것이므로 발동하지 않고 발동 완료로만 표시한다. 지나간 사건으로 쿠폰을 여는
 * 것보다 놓치는 편이 낫다.
 *
 * <h3>정확도 한계</h3>
 * 프레임 집계라 "누가" 첫 킬을 냈는지는 알 수 없다. 처치자를 특정하려면 킬 타임라인
 * 이벤트가 필요하다. 지금은 "이 세트에서 첫 킬이 났다"까지만 판정한다.
 */
@Component
public class FirstBloodDetector {

    private static final Logger log =
            LoggerFactory.getLogger(FirstBloodDetector.class);

    /** gameId → 마지막으로 관측한 세트 전체 누적 킬 */
    private final Map<String, Integer> lastTotalKills = new ConcurrentHashMap<>();

    /** 이미 첫 킬을 발동했거나, 발동 없이 지나간 것으로 확정한 게임 */
    private final Set<String> settled = ConcurrentHashMap.newKeySet();

    private final CouponTriggerPort couponTrigger;

    public FirstBloodDetector(CouponTriggerPort couponTrigger) {
        this.couponTrigger = couponTrigger;
    }

    /**
     * 새 프레임 수신 시 호출. 프레임 중복은 DataCacheService 에서 걸러진 뒤 들어온다.
     *
     * @param externalMatchId 이 세트가 속한 경기. 쿠폰 이벤트를 경기별로 찾는 데 쓴다
     * @param gameStart 게임 시작 시각. 경과 초 계산용이며 아직 확정 전이면 {@code null}
     */
    public void onNewWindowFrame(
            String externalMatchId,
            String gameId,
            WindowResponse.Frame frame,
            Instant gameStart
    ) {
        if (frame == null || gameId == null || settled.contains(gameId)) {
            return;
        }
        Integer totalKills = totalKills(frame);
        if (totalKills == null) {
            // 양 팀 프레임이 모두 비어 있으면 판정 근거가 없다
            return;
        }

        Integer previousKills = lastTotalKills.put(gameId, totalKills);

        if (previousKills == null) {
            // 첫 관측 — 이미 킬이 있으면 첫 킬은 지나갔다. 기준만 세우고 확정한다
            if (totalKills > 0) {
                settled.add(gameId);
                log.debug("게임 {} — 첫 관측에 이미 {}킬, 첫 킬은 지나간 것으로 본다",
                        gameId, totalKills);
            }
            return;
        }

        // 0 에서 벗어나는 순간만 첫 킬이다. 소스가 값을 되돌려도(재접속·보정)
        // previousKills 가 0 이 아니면 발동하지 않는다
        if (previousKills > 0 || totalKills <= 0) {
            return;
        }

        settled.add(gameId);

        Instant frameAt = parseTimestamp(frame.rfc460Timestamp());
        Integer gameTimeSeconds = elapsedSeconds(gameStart, frameAt);
        log.info("첫 킬 감지 — matchId={} gameId={} 누적 {}킬 (경과 {}s)",
                externalMatchId, gameId, totalKills, gameTimeSeconds);

        couponTrigger.fire(
                CouponMatchTrigger.FIRST_BLOOD,
                externalMatchId,
                gameId,
                gameTimeSeconds
        );
    }

    /**
     * 양 팀 누적 킬의 합. 두 팀 프레임이 모두 없으면 {@code null}.
     *
     * <p>한쪽 팀 프레임만 비어 있는 경우는 있는 쪽만 세지 않는다 — 빠진 팀에 킬이
     * 있었다면 0 으로 오판해 첫 킬을 뒤늦게 발동시킬 수 있다.</p>
     */
    private Integer totalKills(WindowResponse.Frame frame) {
        Integer blue = teamKills(frame.blueTeam());
        Integer red = teamKills(frame.redTeam());
        if (blue == null || red == null) {
            return null;
        }
        return blue + red;
    }

    private Integer teamKills(WindowResponse.TeamFrame team) {
        return team == null ? null : team.totalKills();
    }

    /** 게임 시작 후 경과 초. 시작 시각이나 프레임 시각을 모르면 {@code null} */
    private Integer elapsedSeconds(Instant gameStart, Instant frameAt) {
        if (gameStart == null || frameAt == null) {
            return null;
        }
        long seconds = Duration.between(gameStart, frameAt).getSeconds();
        return seconds < 0 ? null : (int) seconds;
    }

    private Instant parseTimestamp(String rfc460Timestamp) {
        if (rfc460Timestamp == null || rfc460Timestamp.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(rfc460Timestamp);
        } catch (Exception exception) {
            log.debug("프레임 시각 파싱 실패: {}", rfc460Timestamp);
            return null;
        }
    }

    /** 게임 종료/비활성화 시 상태 정리 */
    public void clearGame(String gameId) {
        lastTotalKills.remove(gameId);
        settled.remove(gameId);
    }

    /** 외부 데이터 소스 전환 시 이전 소스의 프레임 비교 상태를 비운다. */
    public void clearAll() {
        lastTotalKills.clear();
        settled.clear();
    }
}
