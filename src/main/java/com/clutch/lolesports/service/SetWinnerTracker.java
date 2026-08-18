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
    /** matchId → (teamId → 아직 세트에 귀속하지 못한 gameWins 증가 수) */
    private final Map<String, Map<String, Integer>> pendingWins = new ConcurrentHashMap<>();

    /**
     * 라이브 폴링마다 호출. gameWins 가 오른 팀을 찾아 그 세트의 승자로 기록한다.
     *
     * @param teams 매치의 팀 목록 (result.gameWins 포함)
     * @param games 세트 목록 (state 포함, 번호순)
     */
    public synchronized void observe(String matchId, List<ScheduleResponse.Team> teams,
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

        Map<String, Integer> previous = lastWins.put(matchId, current);
        if (previous == null) {
            restorePendingFromAggregate(matchId, current);
            assignPendingWinners(matchId, games);
            return;
        }

        for (Map.Entry<String, Integer> e : current.entrySet()) {
            int before = previous.getOrDefault(e.getKey(), e.getValue());
            if (e.getValue() > before) {
                pendingWins.computeIfAbsent(matchId, key -> new ConcurrentHashMap<>())
                        .merge(e.getKey(), e.getValue() - before, Integer::sum);
            }
        }
        assignPendingWinners(matchId, games);
    }

    /**
     * 첫 관측에서는 현재 누적 승수에서 이미 확정된 승자 수를 빼 미귀속 증가분을 복원한다.
     * 한 세트만 미확정이거나 한 팀만 연속 승리한 경우에만 순서를 안전하게 확정할 수 있다.
     */
    private void restorePendingFromAggregate(String matchId, Map<String, Integer> current) {
        Map<String, Long> decidedByTeam = new HashMap<>();
        winners.getOrDefault(matchId, Map.of()).values()
                .forEach(teamId -> decidedByTeam.merge(teamId, 1L, Long::sum));

        Map<String, Integer> pending = pendingWins.computeIfAbsent(
                matchId,
                key -> new ConcurrentHashMap<>()
        );
        for (Map.Entry<String, Integer> entry : current.entrySet()) {
            int decided = Math.toIntExact(decidedByTeam.getOrDefault(entry.getKey(), 0L));
            int unresolved = entry.getValue() - decided;
            if (unresolved > 0) {
                pending.put(entry.getKey(), unresolved);
            }
        }
    }

    /** 순서를 확정할 수 있는 미귀속 승수만 completed 세트에 반영한다. */
    private void assignPendingWinners(
            String matchId,
            List<EventDetailsResponse.Game> games
    ) {
        Map<String, Integer> pending = pendingWins.getOrDefault(matchId, Map.of());
        int pendingCount = pending.values().stream().mapToInt(Integer::intValue).sum();
        if (pendingCount == 0) {
            return;
        }

        List<EventDetailsResponse.Game> unresolvedGames = unresolvedCompletedGames(matchId, games);
        if (unresolvedGames.isEmpty()) {
            return;
        }

        List<Map.Entry<String, Integer>> winningTeams = pending.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .toList();
        boolean singleWinnerForAllPending = winningTeams.size() == 1;
        boolean exactlyOneResolvableGame = pendingCount == 1 && unresolvedGames.size() == 1;
        if (!singleWinnerForAllPending && !exactlyOneResolvableGame) {
            log.warn(
                    "매치 {} 미확정 세트 {}건과 승수 증가 {}건의 순서를 특정할 수 없어 보류한다",
                    matchId,
                    unresolvedGames.size(),
                    pendingCount
            );
            return;
        }
        if (pendingCount > unresolvedGames.size()) {
            return;
        }

        String winnerTeamId = winningTeams.getFirst().getKey();
        for (int index = 0; index < pendingCount; index++) {
            String gameId = unresolvedGames.get(index).id();
            winners.computeIfAbsent(matchId, key -> new ConcurrentHashMap<>())
                    .putIfAbsent(gameId, winnerTeamId);
            log.info("세트 승자 확정 — matchId={} gameId={} winner={}",
                    matchId, gameId, winnerTeamId);
        }
        pendingWins.remove(matchId);
    }

    /** 아직 승자를 귀속하지 않은 completed 세트를 번호순으로 반환한다. */
    private List<EventDetailsResponse.Game> unresolvedCompletedGames(
            String matchId,
            List<EventDetailsResponse.Game> games
    ) {
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

        return completed.stream()
                .filter(game -> !known.containsKey(game.id()))
                .toList();
    }

    /** 세트 승리 팀 id — 미확정이면 null */
    public String winnerOf(String matchId, String gameId) {
        if (matchId == null || gameId == null) {
            return null;
        }
        return winners.getOrDefault(matchId, Map.of()).get(gameId);
    }

    /** DB에 저장된 세트 승자를 재시작된 메모리 추적기에 복원한다. */
    public synchronized void restoreWinner(String matchId, String gameId, String winnerTeamId) {
        if (matchId == null || gameId == null || winnerTeamId == null) {
            return;
        }
        winners.computeIfAbsent(matchId, key -> new ConcurrentHashMap<>())
                .putIfAbsent(gameId, winnerTeamId);
        Map<String, Integer> pending = pendingWins.get(matchId);
        if (pending != null) {
            pending.computeIfPresent(winnerTeamId, (key, count) -> count > 1 ? count - 1 : null);
            if (pending.isEmpty()) {
                pendingWins.remove(matchId);
            }
        }
    }

    /** 매치의 확정된 세트 승자 전체 (gameId → teamId) */
    public Map<String, String> winnersOf(String matchId) {
        return Map.copyOf(winners.getOrDefault(matchId, Map.of()));
    }

    /** 매치가 끝나 더 볼 필요가 없을 때 정리 */
    public void clearMatch(String matchId) {
        lastWins.remove(matchId);
        winners.remove(matchId);
        pendingWins.remove(matchId);
    }

    /** /api/debug 노출용 */
    public Map<String, Object> debugSnapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        winners.forEach((matchId, byGame) -> out.put(matchId, new LinkedHashMap<>(byGame)));
        return out;
    }
}
