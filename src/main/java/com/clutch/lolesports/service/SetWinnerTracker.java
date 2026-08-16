package com.clutch.lolesports.service;

import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.dto.external.ScheduleResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 세트 승자 판정.
 *
 * 소스는 세트 승자를 직접 주지 않는다. 대신 매치의 팀별 gameWins 가
 * 세트가 끝날 때마다 오른다 (2026-08-13 KRX vs BFX 실측):
 *
 *   17:39:34  피드 gameState=finished        (실제 종료)
 *   17:42:31  gameWins 0:0, g1 inProgress    (아직 갱신 전)
 *   17:44:43  gameWins 1:0, g1 completed     (약 5분 뒤 반영)
 *
 * 그래서 폴링 사이 gameWins 증가분을 보고 "방금 completed 된 세트"의 승자로 귀속한다.
 * 골드·킬·억제기 같은 지표로 추정하지 않는다 — 소스가 준 값만 쓴다.
 *
 * 판정 시점이 실제 종료보다 약 5분 늦다는 점은 소스 제약이라 우회할 수 없다.
 * 화면의 "세트 종료" 표시는 이 값이 아니라 피드의 finished 를 쓴다.
 */
@Component
public class SetWinnerTracker {

    private static final Logger log = LoggerFactory.getLogger(SetWinnerTracker.class);

    /** matchId → (teamId → 직전 폴링의 gameWins) */
    private final Map<String, Map<String, Integer>> lastWins = new ConcurrentHashMap<>();
    /** matchId → (gameId → 승리 팀 id). 한 번 확정되면 덮어쓰지 않는다 */
    private final Map<String, Map<String, String>> winners = new ConcurrentHashMap<>();
    /** matchId → 이미 승자를 귀속한 세트 수 (completed 순서와 대조용) */
    private final Map<String, Integer> resolvedCount = new ConcurrentHashMap<>();

    /**
     * 라이브 폴링마다 호출. gameWins 가 오른 팀을 찾아 그 세트의 승자로 기록한다.
     *
     * @param teams 매치의 팀 목록 (result.gameWins 포함)
     * @param games 세트 목록 (state 포함, 번호순)
     */
    public void observe(String matchId, List<ScheduleResponse.Team> teams,
                        List<EventDetailsResponse.Game> games) {
        if (matchId == null || teams == null || games == null) {
            return;
        }

        Map<String, Integer> current = new HashMap<>();
        for (ScheduleResponse.Team t : teams) {
            if (t.id() != null && t.result() != null && t.result().gameWins() != null) {
                current.put(t.id(), t.result().gameWins());
            }
        }
        if (current.isEmpty()) {
            return;
        }

        Map<String, Integer> previous = lastWins.get(matchId);
        lastWins.put(matchId, current);
        if (previous == null) {
            // 첫 관측 — 증가분을 알 수 없다. 기준값만 잡고 넘어간다.
            // (서버가 매치 도중 재시작되면 그 이전 세트는 backfill 로 채운다)
            backfillFromCompleted(matchId, current, games);
            return;
        }

        // gameWins 가 오른 팀 = 방금 끝난 세트의 승자
        String winnerTeamId = null;
        int delta = 0;
        for (Map.Entry<String, Integer> e : current.entrySet()) {
            int before = previous.getOrDefault(e.getKey(), e.getValue());
            if (e.getValue() > before) {
                winnerTeamId = e.getKey();
                delta = e.getValue() - before;
            }
        }
        if (winnerTeamId == null) {
            return;
        }
        if (delta > 1) {
            // 폴링을 여러 번 놓쳐 두 세트가 한꺼번에 반영된 경우 — 어느 세트인지 특정할 수 없다
            log.warn("매치 {} gameWins 가 한 번에 {} 증가 — 세트 귀속을 건너뛴다", matchId, delta);
            return;
        }

        String gameId = nextUnresolvedCompletedGame(matchId, games);
        if (gameId == null) {
            log.warn("매치 {} 승자({})를 귀속할 completed 세트를 찾지 못했다", matchId, winnerTeamId);
            return;
        }
        winners.computeIfAbsent(matchId, k -> new ConcurrentHashMap<>()).put(gameId, winnerTeamId);
        resolvedCount.merge(matchId, 1, Integer::sum);
        log.info("세트 승자 확정 — matchId={} gameId={} winner={}", matchId, gameId, winnerTeamId);
    }

    /**
     * 첫 관측인데 이미 끝난 세트가 있으면(서버 재시작 등) 그 세트들은 승자를 알 수 없다.
     * 이후 증가분이 엉뚱한 세트에 붙지 않도록 커서만 맞춰둔다.
     */
    private void backfillFromCompleted(String matchId, Map<String, Integer> current,
                                       List<EventDetailsResponse.Game> games) {
        int completed = (int) games.stream()
                .filter(g -> "completed".equalsIgnoreCase(g.state()))
                .count();
        if (completed > 0) {
            resolvedCount.put(matchId, completed);
            log.info("매치 {} 관측 시작 시점에 이미 {}세트 종료 — 해당 세트 승자는 미확정으로 둔다",
                    matchId, completed);
        }
    }

    /** 아직 승자를 귀속하지 않은 completed 세트 중 가장 앞선 것 */
    private String nextUnresolvedCompletedGame(String matchId, List<EventDetailsResponse.Game> games) {
        Map<String, String> known = winners.getOrDefault(matchId, Map.of());
        List<EventDetailsResponse.Game> completed = new ArrayList<>();
        for (EventDetailsResponse.Game g : games) {
            if (g.id() != null && "completed".equalsIgnoreCase(g.state())) {
                completed.add(g);
            }
        }
        completed.sort((a, b) -> Integer.compare(
                a.number() != null ? a.number() : 0,
                b.number() != null ? b.number() : 0));

        for (EventDetailsResponse.Game g : completed) {
            if (!known.containsKey(g.id())) {
                return g.id();
            }
        }
        return null;
    }

    /** 세트 승리 팀 id — 미확정이면 null */
    public String winnerOf(String matchId, String gameId) {
        if (matchId == null || gameId == null) {
            return null;
        }
        return winners.getOrDefault(matchId, Map.of()).get(gameId);
    }

    /** DB에 저장된 세트 승자를 재시작된 메모리 추적기에 복원한다. */
    public void restoreWinner(String matchId, String gameId, String winnerTeamId) {
        if (matchId == null || gameId == null || winnerTeamId == null) {
            return;
        }
        winners.computeIfAbsent(matchId, key -> new ConcurrentHashMap<>())
                .putIfAbsent(gameId, winnerTeamId);
    }

    /** 매치의 확정된 세트 승자 전체 (gameId → teamId) */
    public Map<String, String> winnersOf(String matchId) {
        return Map.copyOf(winners.getOrDefault(matchId, Map.of()));
    }

    /** 매치가 끝나 더 볼 필요가 없을 때 정리 */
    public void clearMatch(String matchId) {
        lastWins.remove(matchId);
        winners.remove(matchId);
        resolvedCount.remove(matchId);
    }

    /** /api/debug 노출용 */
    public Map<String, Object> debugSnapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        winners.forEach((matchId, byGame) -> out.put(matchId, new LinkedHashMap<>(byGame)));
        return out;
    }
}
