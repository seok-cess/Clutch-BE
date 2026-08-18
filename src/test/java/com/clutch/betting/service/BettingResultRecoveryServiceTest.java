package com.clutch.betting.service;

import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.exception.BettingErrorCode;
import com.clutch.betting.exception.BettingException;
import com.clutch.betting.repository.BettingEventRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class BettingResultRecoveryServiceTest {

    private final BettingEventRepository eventRepository = mock(BettingEventRepository.class);
    private final BetSettlementService settlementService = mock(BetSettlementService.class);
    private final BettingResultRecoveryService service = new BettingResultRecoveryService(
            eventRepository,
            settlementService
    );

    @Test
    void recordsVerifiedWinnerAndTriggersSettlement() {
        BettingEvent event = closedEvent();
        given(eventRepository.findByIdForUpdate(50L)).willReturn(Optional.of(event));

        service.recoverAndSettle(50L, "team-a");

        assertThat(event.getWinnerExternalTeamId()).isEqualTo("team-a");
        verify(settlementService).settle(50L);
    }

    @Test
    void rejectsDifferentWinnerWhenResultWasAlreadyDecided() {
        BettingEvent event = closedEvent();
        event.recordWinner("team-a");
        given(eventRepository.findByIdForUpdate(50L)).willReturn(Optional.of(event));

        assertThatThrownBy(() -> service.recoverAndSettle(50L, "team-b"))
                .isInstanceOf(BettingException.class)
                .extracting(exception -> ((BettingException) exception).getErrorCode())
                .isEqualTo(BettingErrorCode.WINNER_ALREADY_DECIDED);
        verify(settlementService, never()).settle(50L);
    }

    @Test
    void rejectsCancelledEvent() {
        BettingEvent event = closedEvent();
        event.cancel();
        given(eventRepository.findByIdForUpdate(50L)).willReturn(Optional.of(event));

        assertThatThrownBy(() -> service.recoverAndSettle(50L, "team-a"))
                .isInstanceOf(BettingException.class)
                .extracting(exception -> ((BettingException) exception).getErrorCode())
                .isEqualTo(BettingErrorCode.RESULT_NOT_READY);
        verify(settlementService, never()).settle(50L);
    }

    @Test
    void rejectsOpenEventBecauseResultIsNotReadyYet() {
        BettingEvent event = BettingEvent.open(
                "match-1",
                2,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 18, 8, 20),
                LocalDateTime.of(2026, 8, 18, 8, 40)
        );
        given(eventRepository.findByIdForUpdate(50L)).willReturn(Optional.of(event));

        assertThatThrownBy(() -> service.recoverAndSettle(50L, "team-a"))
                .isInstanceOf(BettingException.class)
                .extracting(exception -> ((BettingException) exception).getErrorCode())
                .isEqualTo(BettingErrorCode.RESULT_NOT_READY);
        verify(settlementService, never()).settle(50L);
    }

    @Test
    void throwsEventNotFoundWhenNoEventMatchesId() {
        given(eventRepository.findByIdForUpdate(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.recoverAndSettle(404L, "team-a"))
                .isInstanceOf(BettingException.class)
                .extracting(exception -> ((BettingException) exception).getErrorCode())
                .isEqualTo(BettingErrorCode.EVENT_NOT_FOUND);
        verifyNoInteractions(settlementService);
    }

    @Test
    void reSettlesClosedEventWhenSameWinnerIsRecoveredAgain() {
        BettingEvent event = closedEvent();
        event.recordWinner("team-a");
        given(eventRepository.findByIdForUpdate(50L)).willReturn(Optional.of(event));

        service.recoverAndSettle(50L, "team-a");

        assertThat(event.getWinnerExternalTeamId()).isEqualTo("team-a");
        verify(settlementService).settle(50L);
    }

    @Test
    void skipsSettlementWhenEventIsAlreadySettledWithSameWinner() {
        BettingEvent event = closedEvent();
        event.recordWinner("team-a");
        event.settle();
        given(eventRepository.findByIdForUpdate(50L)).willReturn(Optional.of(event));

        service.recoverAndSettle(50L, "team-a");

        verifyNoInteractions(settlementService);
    }

    private BettingEvent closedEvent() {
        BettingEvent event = BettingEvent.open(
                "match-1",
                2,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 18, 8, 20),
                LocalDateTime.of(2026, 8, 18, 8, 40)
        );
        event.close();
        return event;
    }
}
