package com.clutch.betting.integration.lolesports;

import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.dto.external.ScheduleResponse;
import com.clutch.lolesports.service.DataCacheService;
import com.clutch.lolesports.service.SetWinnerTracker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LolesportsBettingCacheAdapterTest {

    private final DataCacheService dataCacheService = new DataCacheService();
    private final LolesportsBettingCacheAdapter adapter = new LolesportsBettingCacheAdapter(
            dataCacheService,
            new SetWinnerTracker()
    );

    @Test
    void doesNotFinishMatchWhenBestOfIsUnknown() {
        dataCacheService.putLiveMatches(List.of(liveMatch(
                null,
                1,
                List.of(completedSet(1))
        )));

        LiveBettingCache.LiveMatchSnapshot snapshot = adapter.findLiveMatches().getFirst();

        assertThat(snapshot.matchFinished()).isFalse();
    }

    @Test
    void rejectsBetWhenSetCacheIsEmpty() {
        dataCacheService.putLiveMatches(List.of(liveMatch(3, 0, List.of())));

        boolean accepting = adapter.isAcceptingBets("match-1", null, 1);

        assertThat(accepting).isFalse();
    }

    @Test
    void acceptsSpeculativeNextSetOnlyAfterPreviousSetFinishes() {
        dataCacheService.putLiveMatches(List.of(liveMatch(
                3,
                1,
                List.of(completedSet(1))
        )));

        assertThat(adapter.isAcceptingBets("match-1", null, 2)).isTrue();
        assertThat(adapter.isAcceptingBets("match-1", null, 3)).isFalse();
    }

    @Test
    void rejectsAllBetsAfterMatchWinnerIsDecided() {
        dataCacheService.putLiveMatches(List.of(liveMatch(
                3,
                2,
                List.of(completedSet(1), completedSet(2))
        )));

        assertThat(adapter.isAcceptingBets("match-1", null, 3)).isFalse();
    }

    @Test
    void rejectsKnownFutureSetUntilPreviousSetFinishes() {
        dataCacheService.putLiveMatches(List.of(liveMatch(
                3,
                0,
                List.of(
                        activeSet(1),
                        unstartedSet(2)
                )
        )));

        assertThat(adapter.isAcceptingBets("match-1", "game-2", 2)).isFalse();
    }

    @Test
    void rejectsKnownFinishedSetWhenEventGameIdIsNotAttachedYet() {
        dataCacheService.putLiveMatches(List.of(liveMatch(
                3,
                1,
                List.of(completedSet(1), completedSet(2))
        )));

        assertThat(adapter.isAcceptingBets("match-1", null, 2)).isFalse();
    }

    private DataCacheService.LiveMatch liveMatch(
            Integer bestOf,
            int firstTeamWins,
            List<EventDetailsResponse.Game> games
    ) {
        return new DataCacheService.LiveMatch(
                "match-1",
                "1주 차",
                "LCK",
                "2026-08-14T10:00:00Z",
                List.of(
                        team("team-a", firstTeamWins),
                        team("team-b", 0)
                ),
                games,
                bestOf,
                null
        );
    }

    private ScheduleResponse.Team team(String id, int gameWins) {
        return new ScheduleResponse.Team(
                id,
                id,
                id,
                null,
                new ScheduleResponse.Result(null, gameWins),
                null
        );
    }

    private EventDetailsResponse.Game completedSet(int setNumber) {
        return new EventDetailsResponse.Game(
                "game-" + setNumber,
                setNumber,
                "completed",
                List.of()
        );
    }

    private EventDetailsResponse.Game activeSet(int setNumber) {
        return new EventDetailsResponse.Game(
                "game-" + setNumber,
                setNumber,
                "inProgress",
                List.of()
        );
    }

    private EventDetailsResponse.Game unstartedSet(int setNumber) {
        return new EventDetailsResponse.Game(
                "game-" + setNumber,
                setNumber,
                "unstarted",
                List.of()
        );
    }
}
