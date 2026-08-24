package com.clutch.betting.service;

import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.lolesports.client.LolesportsApiClient;
import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.dto.external.ScheduleResponse;
import com.clutch.lolesports.repository.EsportsGameRepository;
import com.clutch.lolesports.service.GamePersistService;
import com.clutch.lolesports.service.SetWinnerTracker;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BettingResultReconciliationServiceTest {

    private final EsportsGameRepository gameRepository = mock(EsportsGameRepository.class);
    private final BettingEventRepository eventRepository = mock(BettingEventRepository.class);
    private final LolesportsApiClient apiClient = mock(LolesportsApiClient.class);
    private final GamePersistService gamePersistService = mock(GamePersistService.class);
    private final BettingEventSynchronizationService synchronizationService =
            mock(BettingEventSynchronizationService.class);
    private final BettingResultReconciliationService service = new BettingResultReconciliationService(
            gameRepository,
            eventRepository,
            apiClient,
            new SetWinnerTracker(),
            gamePersistService,
            synchronizationService
    );

    /**
     * 재시작 뒤에도 이미 정산한 1·2세트 결과를 복원하면, 2:1 최종 스코어에서
     * 마지막 3세트 승자를 안전하게 귀속한다.
     */
    @Test
    void reconcilesLastSetWinnerFromOfficialGameWinsAfterMatchLeavesLiveList() {
        BettingEvent first = closedEvent(1, "game-1");
        first.recordWinner("team-a");
        BettingEvent second = closedEvent(2, "game-2");
        second.recordWinner("team-b");
        BettingEvent third = closedEvent(3, "game-3");
        List<BettingEvent> events = List.of(first, second, third);

        given(gameRepository.findExternalMatchIdsPendingWinnerReconciliation())
                .willReturn(List.of("match-1"));
        given(eventRepository.findExternalMatchIdsWithOpenSpeculativeFutureEvent()).willReturn(List.of());
        given(eventRepository.findExternalMatchIdsWithClosedEventWithoutWinner()).willReturn(List.of());
        given(eventRepository.findAllByExternalMatchId("match-1")).willReturn(events);
        given(apiClient.getEventDetails("match-1")).willReturn(result(2, 1, "completed"));

        service.reconcilePendingResults();

        verify(synchronizationService).closeFinishedEventsForReconciliation("match-1");
        verify(gamePersistService).persistTrackedWinners("match-1");
        verify(synchronizationService).synchronizeConfirmedWinners(
                eq("match-1"),
                argThat(winners -> winners.size() == 1
                        && "team-a".equals(winners.get("game-3")))
        );
        verify(synchronizationService).cancelFutureEventsAfterConfirmedMatch("match-1", 3);
    }

    /** 공식 스코어가 아직 1:1이면 종료 프레임이 있어도 승자·정산을 보류한다. */
    @Test
    void doesNotReconcileWhenOfficialWinnerIsStillUnavailable() {
        BettingEvent first = closedEvent(1, "game-1");
        first.recordWinner("team-a");
        BettingEvent second = closedEvent(2, "game-2");
        second.recordWinner("team-b");
        BettingEvent third = closedEvent(3, "game-3");
        List<BettingEvent> events = List.of(first, second, third);

        given(gameRepository.findExternalMatchIdsPendingWinnerReconciliation())
                .willReturn(List.of("match-1"));
        given(eventRepository.findExternalMatchIdsWithOpenSpeculativeFutureEvent()).willReturn(List.of());
        given(eventRepository.findExternalMatchIdsWithClosedEventWithoutWinner()).willReturn(List.of());
        given(eventRepository.findAllByExternalMatchId("match-1")).willReturn(events);
        given(apiClient.getEventDetails("match-1")).willReturn(result(1, 1, "inProgress"));

        service.reconcilePendingResults();

        verify(gamePersistService, never()).persistTrackedWinners("match-1");
        verify(synchronizationService, never())
                .synchronizeConfirmedWinners(eq("match-1"), org.mockito.ArgumentMatchers.<Map<String, String>>any());
        verify(synchronizationService, never())
                .cancelFutureEventsAfterConfirmedMatch(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyInt());
    }

    /** 세트 승자 정산이 먼저 끝나도 게임 ID 없는 후속 이벤트는 최종 결과를 계속 재조회해 취소한다. */
    @Test
    void cancelsSpeculativeFutureEventAfterOfficialMatchCompletion() {
        BettingEvent first = closedEvent(1, "game-1");
        first.recordWinner("team-a");
        BettingEvent second = closedEvent(2, "game-2");
        second.recordWinner("team-b");
        BettingEvent third = closedEvent(3, "game-3");
        third.recordWinner("team-a");
        BettingEvent speculativeFourth = BettingEvent.open(
                "match-1",
                4,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 19, 10, 35),
                LocalDateTime.of(2026, 8, 19, 10, 55)
        );

        given(gameRepository.findExternalMatchIdsPendingWinnerReconciliation())
                .willReturn(List.of());
        given(eventRepository.findExternalMatchIdsWithOpenSpeculativeFutureEvent())
                .willReturn(List.of("match-1"));
        given(eventRepository.findExternalMatchIdsWithClosedEventWithoutWinner()).willReturn(List.of());
        given(eventRepository.findAllByExternalMatchId("match-1"))
                .willReturn(List.of(first, second, third, speculativeFourth));
        given(apiClient.getEventDetails("match-1")).willReturn(result(2, 1, "completed"));

        service.reconcilePendingResults();

        verify(synchronizationService).cancelFutureEventsAfterConfirmedMatch("match-1", 3);
    }

    /** 게임 적재가 늦어도 닫힌 마지막 세트 이벤트만으로 공식 결과 재조정을 시작한다. */
    @Test
    void reconcilesClosedEventWithoutPersistedGame() {
        BettingEvent first = closedEvent(1, "game-1");
        first.recordWinner("team-a");
        BettingEvent second = closedEvent(2, "game-2");
        second.recordWinner("team-b");
        BettingEvent third = closedEvent(3, "game-3");
        List<BettingEvent> events = List.of(first, second, third);

        given(gameRepository.findExternalMatchIdsPendingWinnerReconciliation()).willReturn(List.of());
        given(eventRepository.findExternalMatchIdsWithOpenSpeculativeFutureEvent()).willReturn(List.of());
        given(eventRepository.findExternalMatchIdsWithClosedEventWithoutWinner())
                .willReturn(List.of("match-1"));
        given(eventRepository.findAllByExternalMatchId("match-1")).willReturn(events);
        given(apiClient.getEventDetails("match-1")).willReturn(result(2, 1, "completed"));

        service.reconcilePendingResults();

        verify(synchronizationService).synchronizeConfirmedWinners(
                eq("match-1"),
                argThat(winners -> "team-a".equals(winners.get("game-3")))
        );
    }

    private EventDetailsResponse result(int firstTeamWins, int secondTeamWins, String thirdGameState) {
        List<ScheduleResponse.Team> teams = List.of(
                team("team-a", "A", firstTeamWins),
                team("team-b", "B", secondTeamWins)
        );
        List<EventDetailsResponse.Game> games = List.of(
                game("game-1", 1, "completed"),
                game("game-2", 2, "completed"),
                game("game-3", 3, thirdGameState)
        );
        EventDetailsResponse.Match match = new EventDetailsResponse.Match(
                teams,
                games,
                new ScheduleResponse.Strategy("bestOf", 3)
        );
        return new EventDetailsResponse(new EventDetailsResponse.Data(
                new EventDetailsResponse.Event("match-1", "match", null, null, match)
        ));
    }

    private ScheduleResponse.Team team(String id, String code, int gameWins) {
        return new ScheduleResponse.Team(
                id,
                code,
                code,
                null,
                new ScheduleResponse.Result(null, gameWins),
                null
        );
    }

    private EventDetailsResponse.Game game(String id, int number, String state) {
        return new EventDetailsResponse.Game(id, number, state, List.of());
    }

    private BettingEvent closedEvent(int setNumber, String gameId) {
        BettingEvent event = BettingEvent.open(
                "match-1",
                setNumber,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 19, 8, 0),
                LocalDateTime.of(2026, 8, 19, 8, 20)
        );
        event.attachGame(gameId);
        event.close();
        return event;
    }
}
