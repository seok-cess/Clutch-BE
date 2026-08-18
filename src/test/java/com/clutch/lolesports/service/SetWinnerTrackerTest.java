package com.clutch.lolesports.service;

import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.dto.external.ScheduleResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SetWinnerTrackerTest {

    private final SetWinnerTracker tracker = new SetWinnerTracker();

    @Test
    void keepsScoreIncreaseUntilCompletedGameBecomesVisible() {
        tracker.observe("match-1", teams(0, 0), List.of(game("game-1", 1, "inProgress")));
        tracker.observe("match-1", teams(1, 0), List.of(game("game-1", 1, "inProgress")));

        assertThat(tracker.winnerOf("match-1", "game-1")).isNull();

        tracker.observe("match-1", teams(1, 0), List.of(game("game-1", 1, "completed")));

        assertThat(tracker.winnerOf("match-1", "game-1")).isEqualTo("team-a");
    }

    @Test
    void restoresSingleCompletedSetFromFirstAggregateObservation() {
        tracker.observe("match-1", teams(0, 1), List.of(game("game-1", 1, "completed")));

        assertThat(tracker.winnerOf("match-1", "game-1")).isEqualTo("team-b");
    }

    @Test
    void restoresConsecutiveWinsBySameTeamWhenOrderIsUnambiguous() {
        tracker.observe("match-1", teams(2, 0), List.of(
                game("game-1", 1, "completed"),
                game("game-2", 2, "completed")
        ));

        assertThat(tracker.winnerOf("match-1", "game-1")).isEqualTo("team-a");
        assertThat(tracker.winnerOf("match-1", "game-2")).isEqualTo("team-a");
    }

    @Test
    void leavesMixedMultipleWinsUnresolvedBecauseSetOrderIsUnknown() {
        tracker.observe("match-1", teams(1, 1), List.of(
                game("game-1", 1, "completed"),
                game("game-2", 2, "completed")
        ));

        assertThat(tracker.winnerOf("match-1", "game-1")).isNull();
        assertThat(tracker.winnerOf("match-1", "game-2")).isNull();
    }

    @Test
    void restoreWinnerSetsKnownWinnerDirectlyWithoutObserving() {
        tracker.restoreWinner("match-1", "game-1", "team-a");

        assertThat(tracker.winnerOf("match-1", "game-1")).isEqualTo("team-a");
        assertThat(tracker.winnersOf("match-1")).containsExactly(Map.entry("game-1", "team-a"));
    }

    @Test
    void restoreWinnerIsIdempotentAndKeepsFirstValue() {
        tracker.restoreWinner("match-1", "game-1", "team-a");
        tracker.restoreWinner("match-1", "game-1", "team-b");

        assertThat(tracker.winnerOf("match-1", "game-1")).isEqualTo("team-a");
    }

    @Test
    void restoreWinnerIgnoresIncompleteArguments() {
        tracker.restoreWinner(null, "game-1", "team-a");
        tracker.restoreWinner("match-1", null, "team-a");
        tracker.restoreWinner("match-1", "game-1", null);

        assertThat(tracker.winnerOf("match-1", "game-1")).isNull();
    }

    @Test
    void combinesRestoredWinnerWithAggregateRestorationOnFirstObservation() {
        // 서버 재시작 후 DB 에서 1세트 승자를 먼저 복원한 상태를 시뮬레이션한다.
        tracker.restoreWinner("match-1", "game-1", "team-a");

        // 첫 라이브 관측에서 누적 승수 2 중 1승은 이미 복원됐으므로 남은 1승만 2세트에 귀속한다.
        tracker.observe("match-1", teams(2, 0), List.of(
                game("game-1", 1, "completed"),
                game("game-2", 2, "completed")
        ));

        assertThat(tracker.winnerOf("match-1", "game-1")).isEqualTo("team-a");
        assertThat(tracker.winnerOf("match-1", "game-2")).isEqualTo("team-a");
    }

    @Test
    void restoreWinnerReducesPendingCountSoLaterPollsResolveRemainingSet() {
        // 두 번의 폴링을 놓쳐 gameWins 가 한 번에 2 증가한 상황 — completed 세트가 하나뿐이라 보류된다.
        tracker.observe("match-1", teams(0, 0), List.of(game("game-1", 1, "inProgress")));
        tracker.observe("match-1", teams(2, 0), List.of(game("game-1", 1, "completed")));

        assertThat(tracker.winnerOf("match-1", "game-1")).isNull();

        // 운영자가 1세트 승자를 DB 복구 경로로 확정하면 보류 중인 증가분에서 1을 차감한다.
        tracker.restoreWinner("match-1", "game-1", "team-a");

        assertThat(tracker.winnerOf("match-1", "game-1")).isEqualTo("team-a");
        assertThat(tracker.winnerOf("match-1", "game-2")).isNull();

        // 2세트가 completed 로 나타나면 남은 보류분 1이 자동으로 귀속된다.
        tracker.observe("match-1", teams(2, 0), List.of(
                game("game-1", 1, "completed"),
                game("game-2", 2, "completed")
        ));

        assertThat(tracker.winnerOf("match-1", "game-2")).isEqualTo("team-a");
    }

    @Test
    void clearMatchRemovesStalePendingWinsPreventingLeakIntoNextObservation() {
        // 두 세트 분량이 한 번에 반영돼 귀속을 보류한 상태를 만든다.
        tracker.observe("match-1", teams(0, 0), List.of(game("game-1", 1, "inProgress")));
        tracker.observe("match-1", teams(2, 0), List.of(game("game-1", 1, "completed")));
        assertThat(tracker.winnerOf("match-1", "game-1")).isNull();

        tracker.clearMatch("match-1");
        assertThat(tracker.winnersOf("match-1")).isEmpty();

        // clearMatch 가 보류분까지 지웠다면, 새 관측은 이전 상태와 무관하게 깨끗하게 시작한다.
        tracker.observe("match-1", teams(0, 1), List.of(game("game-1", 1, "completed")));

        assertThat(tracker.winnerOf("match-1", "game-1")).isEqualTo("team-b");
    }

    private List<ScheduleResponse.Team> teams(int firstWins, int secondWins) {
        return List.of(
                team("team-a", firstWins),
                team("team-b", secondWins)
        );
    }

    private ScheduleResponse.Team team(String id, int wins) {
        return new ScheduleResponse.Team(
                id,
                id,
                id,
                null,
                new ScheduleResponse.Result(null, wins),
                null
        );
    }

    private EventDetailsResponse.Game game(String id, int number, String state) {
        return new EventDetailsResponse.Game(id, number, state, List.of());
    }
}
