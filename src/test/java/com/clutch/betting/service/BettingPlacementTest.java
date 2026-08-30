package com.clutch.betting.service;

import com.clutch.betting.domain.BetPointTransaction;
import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.UserBet;
import com.clutch.betting.dto.BetPlacementResult;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BettingPlacementTest {

    private final BettingEventRepository eventRepository = mock(BettingEventRepository.class);
    private final UserBetRepository userBetRepository = mock(UserBetRepository.class);
    private final BetPointTransactionRepository transactionRepository =
            mock(BetPointTransactionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final BettingLiveStateReader liveStateReader = mock(BettingLiveStateReader.class);
    private final DataCacheService dataCacheService = mock(DataCacheService.class);
    private final SetWinnerTracker setWinnerTracker = mock(SetWinnerTracker.class);
    private final PollingScheduler pollingScheduler = mock(PollingScheduler.class);
    private final BettingService service = new BettingService(
            eventRepository,
            userBetRepository,
            transactionRepository,
            userRepository,
            mock(com.clutch.lolesports.repository.MatchTeamRepository.class),
            liveStateReader,
            dataCacheService,
            setWinnerTracker,
            pollingScheduler,
            Clock.fixed(Instant.parse("2026-08-14T10:01:00Z"), ZoneOffset.UTC)
    );

    @Test
    void placesBetAndStoresStakeTransaction() {
        BettingEvent event = openEvent();
        given(eventRepository.findByIdForUpdate(10L)).willReturn(Optional.of(event));
        given(liveStateReader.isAcceptingBets("match-1", "game-1", 1)).willReturn(false);
        given(userRepository.decreasePointIfEnough(20L, 1_000L)).willReturn(1);
        given(userBetRepository.saveAndFlush(any(UserBet.class))).willAnswer(invocation -> {
            UserBet bet = invocation.getArgument(0);
            ReflectionTestUtils.setField(bet, "id", 30L);
            return bet;
        });
        given(transactionRepository.saveAndFlush(any(BetPointTransaction.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(userRepository.findPointById(20L)).willReturn(Optional.of(9_000L));

        BetPlacementResult result = service.place(20L, 10L, "team-a", 1_000L);

        assertThat(result.userBetId()).isEqualTo(30L);
        assertThat(result.userId()).isEqualTo(20L);
        verify(userRepository).decreasePointIfEnough(20L, 1_000L);
        verify(transactionRepository).saveAndFlush(any(BetPointTransaction.class));
    }

    @Test
    void propagatesStakeTransactionIntegrityFailure() {
        BettingEvent event = openEvent();
        given(eventRepository.findByIdForUpdate(10L)).willReturn(Optional.of(event));
        given(liveStateReader.isAcceptingBets("match-1", "game-1", 1)).willReturn(true);
        given(userRepository.decreasePointIfEnough(20L, 1_000L)).willReturn(1);
        given(userBetRepository.saveAndFlush(any(UserBet.class))).willAnswer(invocation -> {
            UserBet bet = invocation.getArgument(0);
            ReflectionTestUtils.setField(bet, "id", 30L);
            return bet;
        });
        given(transactionRepository.saveAndFlush(any(BetPointTransaction.class)))
                .willThrow(new DataIntegrityViolationException("ledger constraint"));

        assertThatThrownBy(() -> service.place(20L, 10L, "team-a", 1_000L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsClosedOrExpiredEvent() {
        BettingEvent event = openEvent();
        event.close();
        given(eventRepository.findByIdForUpdate(10L)).willReturn(Optional.of(event));

        assertBettingError(
                () -> service.place(20L, 10L, "team-a", 1_000L),
                BettingErrorCode.EVENT_NOT_OPEN
        );
    }

    @Test
    void rejectsLaterSetBetWhenLiveCacheIsUnavailable() {
        BettingEvent event = openEvent(2);
        given(eventRepository.findByIdForUpdate(10L)).willReturn(Optional.of(event));
        given(liveStateReader.isAcceptingBets("match-1", "game-2", 2)).willReturn(false);

        assertBettingError(
                () -> service.place(20L, 10L, "team-a", 1_000L),
                BettingErrorCode.LIVE_DATA_UNAVAILABLE
        );
    }

    @Test
    void rejectsInsufficientPoint() {
        BettingEvent event = openEvent();
        given(eventRepository.findByIdForUpdate(10L)).willReturn(Optional.of(event));
        given(liveStateReader.isAcceptingBets("match-1", "game-1", 1)).willReturn(true);
        given(userRepository.decreasePointIfEnough(20L, 1_000L)).willReturn(0);
        given(userRepository.existsById(20L)).willReturn(true);

        assertBettingError(
                () -> service.place(20L, 10L, "team-a", 1_000L),
                BettingErrorCode.INSUFFICIENT_POINT
        );
    }

    private BettingEvent openEvent() {
        return openEvent(1);
    }

    private BettingEvent openEvent(int setNumber) {
        BettingEvent event = BettingEvent.open(
                "match-1",
                setNumber,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 0),
                LocalDateTime.of(2026, 8, 14, 10, 2)
        );
        event.attachGame("game-" + setNumber);
        return event;
    }

    private void assertBettingError(Runnable action, BettingErrorCode expectedError) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(
                        BettingException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(expectedError)
                );
    }
}
