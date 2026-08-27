package com.clutch.betting.service;

import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.exception.BettingErrorCode;
import com.clutch.betting.exception.BettingException;
import com.clutch.betting.live.BettingLiveStateReader;
import com.clutch.betting.repository.BetPointTransactionRepository;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.betting.repository.UserBetRepository;
import com.clutch.lolesports.service.DataCacheService;
import com.clutch.lolesports.service.PollingScheduler;
import com.clutch.lolesports.service.SetWinnerTracker;
import com.clutch.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BettingWinnerRecoveryTest {

    private final BettingEventRepository eventRepository = mock(BettingEventRepository.class);
    private final UserBetRepository userBetRepository = mock(UserBetRepository.class);
    private final BettingService service = new BettingService(
            eventRepository,
            userBetRepository,
            mock(BetPointTransactionRepository.class),
            mock(UserRepository.class),
            mock(BettingLiveStateReader.class),
            mock(DataCacheService.class),
            mock(SetWinnerTracker.class),
            mock(PollingScheduler.class),
            Clock.systemUTC()
    );

    @Test
    void recordsVerifiedWinnerAndSettlesEvent() {
        BettingEvent event = closedEvent();
        given(eventRepository.findByIdForUpdate(50L)).willReturn(Optional.of(event));
        given(userBetRepository.findAllByBettingEventIdAndStatusForUpdate(50L, UserBetStatus.PLACED))
                .willReturn(List.of());

        service.recoverWinnerAndSettle(50L, "team-a");

        assertThat(event.getWinnerExternalTeamId()).isEqualTo("team-a");
        assertThat(event.getStatus()).isEqualTo(BettingEventStatus.SETTLED);
    }

    @Test
    void rejectsDifferentWinnerWhenResultWasAlreadyDecided() {
        BettingEvent event = closedEvent();
        event.recordWinner("team-a");
        given(eventRepository.findByIdForUpdate(50L)).willReturn(Optional.of(event));

        assertThatThrownBy(() -> service.recoverWinnerAndSettle(50L, "team-b"))
                .isInstanceOf(BettingException.class)
                .extracting(exception -> ((BettingException) exception).getErrorCode())
                .isEqualTo(BettingErrorCode.WINNER_ALREADY_DECIDED);
        verify(userBetRepository, never()).findAllByBettingEventIdAndStatusForUpdate(50L, UserBetStatus.PLACED);
    }

    @Test
    void rejectsCancelledEvent() {
        BettingEvent event = closedEvent();
        event.cancel();
        given(eventRepository.findByIdForUpdate(50L)).willReturn(Optional.of(event));

        assertThatThrownBy(() -> service.recoverWinnerAndSettle(50L, "team-a"))
                .isInstanceOf(BettingException.class)
                .extracting(exception -> ((BettingException) exception).getErrorCode())
                .isEqualTo(BettingErrorCode.RESULT_NOT_READY);
        verify(userBetRepository, never()).findAllByBettingEventIdAndStatusForUpdate(50L, UserBetStatus.PLACED);
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
