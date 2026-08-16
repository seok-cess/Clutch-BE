package com.clutch.betting.live;

import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.dto.external.ScheduleResponse;
import com.clutch.lolesports.dto.external.WindowResponse;
import com.clutch.lolesports.service.DataCacheService;
import com.clutch.lolesports.service.SetWinnerTracker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LolesportsLiveBettingDataProviderTest {

    private final DataCacheService dataCacheService = new DataCacheService();
    private final LolesportsLiveBettingDataProvider provider = new LolesportsLiveBettingDataProvider(
            dataCacheService,
            new SetWinnerTracker()
    );

    @Test
    void doesNotFinishMatchWhenBestOfIsUnknown() {
        dataCacheService.putBettingMatches(List.of(liveMatch(
                null,
                1,
                List.of(completedSet(1))
        )));

        LiveBettingDataProvider.LiveMatchSnapshot snapshot = provider.findLiveMatches().getFirst();

        assertThat(snapshot.matchFinished()).isFalse();
    }

    @Test
    void acceptsScheduledFirstSetBeforeGameListIsAvailable() {
        dataCacheService.putBettingMatches(List.of(liveMatch(3, 0, List.of())));

        boolean accepting = provider.isAcceptingBets("match-1", null, 1);

        assertThat(accepting).isTrue();
    }

    @Test
    void acceptsSpeculativeNextSetOnlyAfterPreviousSetFinishes() {
        dataCacheService.putBettingMatches(List.of(liveMatch(
                3,
                1,
                List.of(completedSet(1))
        )));
        recordFinished("game-1", "2026-08-14T10:20:00Z");

        assertThat(provider.isAcceptingBets("match-1", null, 2)).isTrue();
        assertThat(provider.isAcceptingBets("match-1", null, 3)).isFalse();
    }

    @Test
    void rejectsAllBetsAfterMatchWinnerIsDecided() {
        dataCacheService.putBettingMatches(List.of(liveMatch(
                3,
                2,
                List.of(completedSet(1), completedSet(2))
        )));

        assertThat(provider.isAcceptingBets("match-1", null, 3)).isFalse();
    }

    @Test
    void rejectsKnownFutureSetUntilPreviousSetFinishes() {
        dataCacheService.putBettingMatches(List.of(liveMatch(
                3,
                0,
                List.of(
                        activeSet(1),
                        unstartedSet(2)
                )
        )));

        assertThat(provider.isAcceptingBets("match-1", "game-2", 2)).isFalse();
    }

    @Test
    void rejectsKnownFinishedSetWhenEventGameIdIsNotAttachedYet() {
        dataCacheService.putBettingMatches(List.of(liveMatch(
                3,
                1,
                List.of(completedSet(1), completedSet(2))
        )));

        assertThat(provider.isAcceptingBets("match-1", null, 2)).isFalse();
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

    private void recordFinished(String gameId, String finishedAt) {
        dataCacheService.addWindowFrames(
                gameId,
                null,
                List.of(new WindowResponse.Frame(finishedAt, "finished", null, null))
        );
    }
}
