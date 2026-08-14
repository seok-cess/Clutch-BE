package com.clutch.betting.service;

import com.clutch.betting.domain.BetPointTransaction;
import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.UserBet;
import com.clutch.betting.exception.BettingErrorCode;
import com.clutch.betting.exception.BettingException;
import com.clutch.betting.integration.lolesports.LiveBettingCache;
import com.clutch.betting.repository.BetPointTransactionRepository;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.betting.repository.UserBetRepository;
import com.clutch.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
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

class BetPlacementServiceTest {

    private final BettingEventRepository eventRepository = mock(BettingEventRepository.class);
    private final UserBetRepository userBetRepository = mock(UserBetRepository.class);
    private final BetPointTransactionRepository transactionRepository =
            mock(BetPointTransactionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final LiveBettingCache liveBettingCache = mock(LiveBettingCache.class);
    private final BetPlacementService service = new BetPlacementService(
            eventRepository,
            userBetRepository,
            transactionRepository,
            userRepository,
            liveBettingCache,
            Clock.fixed(Instant.parse("2026-08-14T10:01:00Z"), ZoneOffset.UTC)
    );

    @Test
    void placesBetAndStoresStakeTransaction() {
        BettingEvent event = openEvent();
        given(eventRepository.findByIdForUpdate(10L)).willReturn(Optional.of(event));
        given(liveBettingCache.isAcceptingBets("match-1", "game-1", 1)).willReturn(true);
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
        verify(userRepository).decreasePointIfEnough(20L, 1_000L);
        verify(transactionRepository).saveAndFlush(any(BetPointTransaction.class));
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
    void rejectsBetWhenLiveCacheIsUnavailable() {
        BettingEvent event = openEvent();
        given(eventRepository.findByIdForUpdate(10L)).willReturn(Optional.of(event));
        given(liveBettingCache.isAcceptingBets("match-1", "game-1", 1)).willReturn(false);

        assertBettingError(
                () -> service.place(20L, 10L, "team-a", 1_000L),
                BettingErrorCode.LIVE_DATA_UNAVAILABLE
        );
    }

    @Test
    void rejectsInsufficientPoint() {
        BettingEvent event = openEvent();
        given(eventRepository.findByIdForUpdate(10L)).willReturn(Optional.of(event));
        given(liveBettingCache.isAcceptingBets("match-1", "game-1", 1)).willReturn(true);
        given(userRepository.decreasePointIfEnough(20L, 1_000L)).willReturn(0);
        given(userRepository.existsById(20L)).willReturn(true);

        assertBettingError(
                () -> service.place(20L, 10L, "team-a", 1_000L),
                BettingErrorCode.INSUFFICIENT_POINT
        );
    }

    private BettingEvent openEvent() {
        BettingEvent event = BettingEvent.open(
                "match-1",
                1,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 0)
        );
        event.attachGame("game-1", null, java.time.Duration.ofMinutes(2));
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
