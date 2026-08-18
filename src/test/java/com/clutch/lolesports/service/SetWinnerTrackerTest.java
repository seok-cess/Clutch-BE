package com.clutch.lolesports.service;

import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.dto.external.ScheduleResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

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
