package com.clutch.lolesports.service;

import com.clutch.lolesports.dto.external.WindowResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 펜타킬 감지기 (뼈대만).
 *
 * 새 window 프레임이 반영될 때마다 호출되어 참가자별 kills 스냅샷을 유지한다.
 * 실제 판정 로직(델타 기반 연속킬 판별, 시간창 처리 등)과 쿠폰 발급 연동은 다음 단계 TODO.
 */
@Component
public class PentakillDetector {

    private static final Logger log = LoggerFactory.getLogger(PentakillDetector.class);

    /** gameId → (participantId → 마지막으로 관측한 kills) */
    private final Map<String, Map<Integer, Integer>> lastKillsByGame = new ConcurrentHashMap<>();

    /**
     * 새 프레임 수신 시 호출. 프레임 중복은 DataCacheService 에서 걸러진 뒤 들어온다.
     */
    public void onNewWindowFrame(String gameId, WindowResponse.Frame frame) {
        Map<Integer, Integer> lastKills = lastKillsByGame.computeIfAbsent(gameId, k -> new HashMap<>());

        for (WindowResponse.TeamFrame team : new WindowResponse.TeamFrame[]{frame.blueTeam(), frame.redTeam()}) {
            if (team == null || team.participants() == null) {
                continue;
            }
            for (WindowResponse.ParticipantFrame p : team.participants()) {
                if (p.participantId() == null || p.kills() == null) {
                    continue;
                }
                Integer prev = lastKills.get(p.participantId());
                int delta = (prev == null) ? 0 : p.kills() - prev;

                // TODO: 펜타킬 판정 로직
                //  - 프레임 해상도(약 10초) 안에서 delta >= 5 면 펜타킬 후보로 볼 수 있으나,
                //    프레임 폴링 누락/일시정지 등으로 오탐 가능 → 시간창 기반 누적 판정 필요
                //  - 정확한 판정에는 이벤트 단위 데이터(킬 타임라인)가 필요할 수 있음
                //  - 판정 확정 시 쿠폰 발급 트리거 연결 (다음 단계)
                if (delta >= 5) {
                    log.info("[TODO] 펜타킬 후보 감지: gameId={}, participantId={}, delta={}", gameId, p.participantId(), delta);
                }

                lastKills.put(p.participantId(), p.kills());
            }
        }
    }

    /** 게임 종료/비활성화 시 상태 정리 */
    public void clearGame(String gameId) {
        lastKillsByGame.remove(gameId);
    }

    /** 외부 데이터 소스 전환 시 이전 소스의 프레임 비교 상태를 비운다. */
    public void clearAll() {
        lastKillsByGame.clear();
    }
}
