package com.clutch.lolesports.service;

import com.clutch.coupon.contract.trigger.CouponMatchTrigger;
import com.clutch.coupon.contract.trigger.CouponTriggerPort;
import com.clutch.lolesports.dto.external.WindowResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 펜타킬 감지기.
 *
 * <h3>왜 델타 한 번으로 판정하지 않나</h3>
 * window 프레임은 약 10초 간격이라 프레임 사이의 킬은 한 덩어리로 뭉쳐서 들어온다.
 * 프레임 하나에서 {@code delta >= 5} 를 보고 판정하면 두 가지가 다 틀린다.
 * <ul>
 *   <li><b>누락</b> — 5킬이 프레임 경계에 걸치면 3킬 + 2킬로 쪼개져 영영 안 잡힌다.
 *       실제 펜타킬은 보통 10~20초에 걸쳐 일어나므로 이쪽이 훨씬 흔하다.</li>
 *   <li><b>오탐</b> — 폴링이 밀려 프레임이 통째로 빠지면 30초치 킬이 한 델타로 들어와,
 *       느리게 쌓인 5킬이 펜타킬로 둔갑한다.</li>
 * </ul>
 *
 * <h3>판정 방식</h3>
 * 참가자별로 "언제 몇 킬이 늘었는지"를 시간과 함께 큐에 쌓고, {@link #PENTAKILL_WINDOW}
 * 안에 들어온 킬의 합이 5 이상이면 펜타킬로 본다. 창을 벗어난 기록은 버린다.
 *
 * <p>프레임 간격이 창보다 크면(폴링 지연) 그 델타는 신뢰할 수 없으므로 버린다.
 * 놓치는 편이 없는 펜타킬로 쿠폰을 여는 것보다 낫다.</p>
 *
 * <h3>정확도 한계</h3>
 * 진짜 펜타킬(한 선수가 상대 5명을 연속으로, 중간에 다른 아군 킬 없이)은 프레임 단위
 * 집계로는 완전히 구분할 수 없다. 킬 타임라인 이벤트가 있어야 엄밀해진다.
 * 지금은 "짧은 시간에 5킬"까지만 판정하며, 이는 실제 펜타킬을 거의 모두 포함하되
 * 드물게 난전 상황을 함께 잡을 수 있다.
 */
@Component
public class PentakillDetector {

    private static final Logger log = LoggerFactory.getLogger(PentakillDetector.class);

    /** 이 시간 안에 5킬이 쌓이면 펜타킬로 본다. 실제 펜타킬은 대개 30초 안에 끝난다 */
    private static final Duration PENTAKILL_WINDOW = Duration.ofSeconds(30);

    /** 펜타킬 성립에 필요한 킬 수 */
    private static final int PENTAKILL_KILLS = 5;

    /**
     * 프레임 간격이 이보다 벌어지면 그 델타는 버린다.
     * 폴링이 밀려 뭉친 킬을 짧은 시간에 난 것으로 오해하지 않기 위해서다.
     */
    private static final Duration MAX_TRUSTED_GAP = PENTAKILL_WINDOW;

    /** gameId → 게임별 감지 상태 */
    private final Map<String, GameState> states = new ConcurrentHashMap<>();

    private final CouponTriggerPort couponTrigger;

    public PentakillDetector(CouponTriggerPort couponTrigger) {
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
        if (frame == null) {
            return;
        }
        Instant frameAt = parseTimestamp(frame.rfc460Timestamp());
        if (frameAt == null) {
            // 시각을 못 읽으면 시간창 판정 자체가 불가능하다
            return;
        }

        GameState state = states.computeIfAbsent(gameId, k -> new GameState());

        for (WindowResponse.TeamFrame team
                : new WindowResponse.TeamFrame[]{frame.blueTeam(), frame.redTeam()}) {
            if (team == null || team.participants() == null) {
                continue;
            }
            for (WindowResponse.ParticipantFrame participant : team.participants()) {
                observeParticipant(
                        state, externalMatchId, gameId, frameAt, gameStart, participant
                );
            }
        }
    }

    private void observeParticipant(
            GameState state,
            String externalMatchId,
            String gameId,
            Instant frameAt,
            Instant gameStart,
            WindowResponse.ParticipantFrame participant
    ) {
        if (participant == null
                || participant.participantId() == null
                || participant.kills() == null) {
            return;
        }
        int participantId = participant.participantId();
        int kills = participant.kills();

        Integer previousKills = state.lastKills.put(participantId, kills);
        Instant previousAt = state.lastSeenAt.put(participantId, frameAt);

        if (previousKills == null) {
            // 첫 관측 — 비교 기준만 세운다. 게임 도중 합류하면 누적 킬이 이미 있어
            // 이를 증가분으로 세면 즉시 오탐이 난다
            return;
        }

        int delta = kills - previousKills;
        if (delta <= 0) {
            // 소스가 값을 되돌리는 경우가 있다(재접속·보정). 기준만 갱신하고 넘어간다
            return;
        }

        // 폴링 공백 뒤 뭉쳐 들어온 킬은 "짧은 시간에 났다"고 볼 수 없다
        if (previousAt != null
                && Duration.between(previousAt, frameAt).compareTo(MAX_TRUSTED_GAP) > 0) {
            state.recentKills.remove(participantId);
            log.debug("게임 {} 참가자 {} — 프레임 간격이 벌어져 {}킬 증가분을 버린다",
                    gameId, participantId, delta);
            return;
        }

        Deque<KillBurst> bursts = state.recentKills
                .computeIfAbsent(participantId, k -> new ArrayDeque<>());
        bursts.addLast(new KillBurst(frameAt, delta));

        // 창을 벗어난 기록 정리
        Instant cutoff = frameAt.minus(PENTAKILL_WINDOW);
        while (!bursts.isEmpty() && bursts.peekFirst().at().isBefore(cutoff)) {
            bursts.removeFirst();
        }

        int killsInWindow = bursts.stream().mapToInt(KillBurst::count).sum();
        if (killsInWindow < PENTAKILL_KILLS) {
            return;
        }

        // 한 게임에서 같은 참가자를 두 번 발동시키지 않는다.
        // 창이 겹치면 다음 킬에서 또 5가 넘어 연달아 열릴 수 있다
        if (!state.fired.add(participantId)) {
            return;
        }
        bursts.clear();

        Integer gameTimeSeconds = elapsedSeconds(gameStart, frameAt);
        log.info("펜타킬 감지 — matchId={} gameId={} participantId={} {}초간 {}킬 (경과 {}s)",
                externalMatchId, gameId, participantId,
                PENTAKILL_WINDOW.toSeconds(), killsInWindow, gameTimeSeconds);

        couponTrigger.fire(
                CouponMatchTrigger.PENTAKILL,
                externalMatchId,
                gameId,
                gameTimeSeconds
        );
    }

    /** 게임 시작 후 경과 초. 시작 시각을 아직 모르면 {@code null} */
    private Integer elapsedSeconds(Instant gameStart, Instant frameAt) {
        if (gameStart == null) {
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
        states.remove(gameId);
    }

    /** 외부 데이터 소스 전환 시 이전 소스의 프레임 비교 상태를 비운다. */
    public void clearAll() {
        states.clear();
    }

    /** 게임 하나의 감지 상태 */
    private static final class GameState {
        /** participantId → 마지막으로 관측한 누적 킬 */
        private final Map<Integer, Integer> lastKills = new HashMap<>();
        /** participantId → 마지막 관측 프레임 시각 (폴링 공백 판단용) */
        private final Map<Integer, Instant> lastSeenAt = new HashMap<>();
        /** participantId → 시간창 안의 킬 증가분 기록 */
        private final Map<Integer, Deque<KillBurst>> recentKills = new HashMap<>();
        /** 이미 펜타킬로 발동한 참가자 */
        private final Set<Integer> fired = new HashSet<>();
    }

    /** 한 프레임에서 늘어난 킬 수와 그 시각 */
    private record KillBurst(Instant at, int count) {
    }
}
