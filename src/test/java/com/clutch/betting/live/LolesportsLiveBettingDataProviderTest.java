package com.clutch.betting.live;

import com.clutch.betting.config.BettingProperties;
import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.dto.external.ScheduleResponse;
import com.clutch.lolesports.dto.external.WindowResponse;
import com.clutch.lolesports.entity.EsportsMatch;
import com.clutch.lolesports.repository.EsportsGameRepository;
import com.clutch.lolesports.repository.EsportsMatchRepository;
import com.clutch.lolesports.service.DataCacheService;
import com.clutch.lolesports.service.SetWinnerTracker;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class LolesportsLiveBettingDataProviderTest {

    private final DataCacheService dataCacheService = new DataCacheService();
    private final SetWinnerTracker setWinnerTracker = new SetWinnerTracker();
    private final EsportsGameRepository esportsGameRepository = mock(EsportsGameRepository.class);
    private final EsportsMatchRepository esportsMatchRepository = mock(EsportsMatchRepository.class);
    private final LolesportsLiveBettingDataProvider provider = new LolesportsLiveBettingDataProvider(
            dataCacheService,
            setWinnerTracker,
            esportsGameRepository,
            esportsMatchRepository,
            new BettingProperties(
                    Duration.ofMinutes(20),
                    Duration.ofMinutes(1),
                    Duration.ofMinutes(20),
                    Duration.ofSeconds(1)
            ),
            Clock.fixed(Instant.parse("2026-08-14T10:01:00Z"), ZoneOffset.UTC)
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

    /** 라이브 응답의 전략이 일시적으로 없으면 DB에 적재한 bestOf로 최종 세트를 판별한다. */
    @Test
    void restoresBestOfFromPersistedMatchWhenLiveCacheDoesNotHaveIt() {
        dataCacheService.putBettingMatches(List.of(liveMatch(
                null,
                1,
                List.of(completedSet(1), completedSet(2), completedSet(3))
        )));
        EsportsMatch persistedMatch = mock(EsportsMatch.class);
        given(persistedMatch.getBestOf()).willReturn(3);
        given(esportsMatchRepository.findByExternalMatchId("match-1"))
                .willReturn(Optional.of(persistedMatch));

        LiveBettingDataProvider.LiveMatchSnapshot snapshot = provider.findLiveMatches().getFirst();

        assertThat(snapshot.bestOf()).isEqualTo(3);
    }

    /** 전략 값이 누락돼도 상세 응답의 최대 세트 번호로 BO3의 마지막 세트를 인식한다. */
    @Test
    void derivesBestOfFromKnownGameNumbersWhenPersistedMatchIsNotAvailable() {
        dataCacheService.putBettingMatches(List.of(liveMatch(
                null,
                1,
                List.of(unstartedSet(1), unstartedSet(2), unstartedSet(3))
        )));
        given(esportsMatchRepository.findByExternalMatchId("match-1"))
                .willReturn(Optional.empty());

        LiveBettingDataProvider.LiveMatchSnapshot snapshot = provider.findLiveMatches().getFirst();

        assertThat(snapshot.bestOf()).isEqualTo(3);
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

    /** 종료 프레임 캐시가 정리된 뒤에도 공식 완료 상태면 다음 세트 배팅을 허용한다. */
    @Test
    void acceptsNextSetAfterOfficialCompletionWithoutFeedFinishTimestamp() {
        dataCacheService.putBettingMatches(List.of(liveMatch(
                3,
                1,
                List.of(completedSet(1), unstartedSet(2))
        )));

        assertThat(provider.isAcceptingBets("match-1", "game-2", 2)).isTrue();
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
    void doesNotFinishMatchWhileAnotherSetIsInProgress() {
        dataCacheService.putBettingMatches(List.of(liveMatch(
                3,
                2,
                List.of(completedSet(1), activeSet(2))
        )));

        LiveBettingDataProvider.LiveMatchSnapshot snapshot = provider.findLiveMatches().getFirst();

        assertThat(snapshot.matchFinished()).isFalse();
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

    /** BO3의 3세트가 끝난 뒤 잘못 남은 4세트 이벤트에도 배팅을 허용하지 않는다. */
    @Test
    void rejectsSetBeyondBestOfBeforeOfficialMatchCompletion() {
        dataCacheService.putBettingMatches(List.of(liveMatch(
                3,
                1,
                List.of(completedSet(1), completedSet(2), completedSet(3))
        )));
        recordFinished("game-3", "2026-08-14T10:30:00Z");

        assertThat(provider.isAcceptingBets("match-1", null, 4)).isFalse();
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

    /** 실제 세트 시작 1분 뒤에는 동기화 주기 사이에도 새 배팅을 막는다. */
    @Test
    void rejectsNextSetOneMinuteAfterActualGameStart() {
        dataCacheService.putBettingMatches(List.of(liveMatch(
                3,
                1,
                List.of(completedSet(1), activeSet(2))
        )));
        recordFinished("game-1", "2026-08-14T09:50:00Z");
        dataCacheService.setGameStart("game-2", Instant.parse("2026-08-14T10:00:00Z"));

        assertThat(provider.isAcceptingBets("match-1", "game-2", 2)).isFalse();
    }

    /** 첫 세트도 실제 시작 후 1분이 지나면 공식 일정과 무관하게 마감한다. */
    @Test
    void closesFirstSetOneMinuteAfterFeedStart() {
        dataCacheService.putBettingMatches(List.of(liveMatch(
                3,
                0,
                List.of(activeSet(1))
        )));
        dataCacheService.setGameStart("game-1", Instant.parse("2026-08-14T10:00:00Z"));

        assertThat(provider.isAcceptingBets("match-1", "game-1", 1)).isFalse();
    }

    @Test
    void restoresPersistedWinnerAfterTrackerRestart() {
        dataCacheService.putBettingMatches(List.of(liveMatch(
                3,
                1,
                List.of(completedSet(1))
        )));
        given(esportsGameRepository.findWinnerExternalTeamId("game-1"))
                .willReturn(Optional.of("team-a"));

        LiveBettingDataProvider.SetSnapshot set = provider.findLiveMatches()
                .getFirst()
                .sets()
                .getFirst();

        assertThat(set.winnerExternalTeamId()).isEqualTo("team-a");
        assertThat(setWinnerTracker.winnerOf("match-1", "game-1")).isEqualTo("team-a");
    }

    private DataCacheService.LiveMatch liveMatch(
            Integer bestOf,
            int firstTeamWins,
            List<EventDetailsResponse.Game> games
    ) {
        String activeGameId = games.stream()
                .filter(game -> "inProgress".equalsIgnoreCase(game.state()))
                .map(EventDetailsResponse.Game::id)
                .findFirst()
                .orElse(null);
        return new DataCacheService.LiveMatch(
                "match-1",
                "1주 차",
                "LCK",
                "2026-08-14T10:00:00Z",
                bestOf,
                List.of(
                        team("team-a", firstTeamWins),
                        team("team-b", 0)
                ),
                games,
                activeGameId
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
