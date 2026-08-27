package com.clutch.betting.service;

import com.clutch.betting.domain.BetPointTransaction;
import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.domain.UserBet;
import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.repository.BetPointTransactionRepository;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.betting.live.BettingLiveStateReader;
import com.clutch.betting.repository.UserBetRepository;
import com.clutch.lolesports.service.DataCacheService;
import com.clutch.lolesports.service.PollingScheduler;
import com.clutch.lolesports.service.SetWinnerTracker;
import com.clutch.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class BettingSettlementTest {

    private final BettingEventRepository eventRepository = mock(BettingEventRepository.class);
    private final UserBetRepository userBetRepository = mock(UserBetRepository.class);
    private final BetPointTransactionRepository transactionRepository =
            mock(BetPointTransactionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final BettingService service = new BettingService(
            eventRepository,
            userBetRepository,
            transactionRepository,
            userRepository,
            mock(BettingLiveStateReader.class),
            mock(DataCacheService.class),
            mock(SetWinnerTracker.class),
            mock(PollingScheduler.class),
            java.time.Clock.systemUTC()
    );

    @Test
    void distributesPoolAfterTenPercentFeeAndSettlesLosers() {
        BettingEvent event = settledReadyEvent();
        UserBet firstWinner = userBet(100L, 10L, "team-a", 1_000L);
        UserBet secondWinner = userBet(200L, 20L, "team-a", 2_000L);
        UserBet loser = userBet(300L, 30L, "team-b", 7_000L);
        given(eventRepository.findByIdForUpdate(1L)).willReturn(Optional.of(event));
        given(userBetRepository.findAllByBettingEventIdAndStatusForUpdate(
                1L,
                UserBetStatus.PLACED
        )).willReturn(List.of(firstWinner, secondWinner, loser));
        given(userRepository.increasePoint(10L, 3_000L)).willReturn(1);
        given(userRepository.increasePoint(20L, 6_000L)).willReturn(1);

        service.settle(1L);

        assertThat(event.getStatus()).isEqualTo(BettingEventStatus.SETTLED);
        assertThat(firstWinner.getStatus()).isEqualTo(UserBetStatus.WON);
        assertThat(secondWinner.getStatus()).isEqualTo(UserBetStatus.WON);
        assertThat(loser.getStatus()).isEqualTo(UserBetStatus.LOST);
        verify(userRepository).increasePoint(10L, 3_000L);
        verify(userRepository).increasePoint(20L, 6_000L);
        verify(transactionRepository, times(2)).save(any(BetPointTransaction.class));
    }

    @Test
    void retainsFractionalPayoutRemainderAsOperatingFee() {
        BettingEvent event = settledReadyEvent();
        UserBet firstWinner = userBet(100L, 10L, "team-a", 1_001L);
        UserBet secondWinner = userBet(200L, 20L, "team-a", 1_000L);
        UserBet loser = userBet(300L, 30L, "team-b", 1_000L);
        given(eventRepository.findByIdForUpdate(1L)).willReturn(Optional.of(event));
        given(userBetRepository.findAllByBettingEventIdAndStatusForUpdate(
                1L,
                UserBetStatus.PLACED
        )).willReturn(List.of(firstWinner, secondWinner, loser));
        given(userRepository.increasePoint(10L, 1_351L)).willReturn(1);
        given(userRepository.increasePoint(20L, 1_349L)).willReturn(1);

        service.settle(1L);

        verify(userRepository).increasePoint(10L, 1_351L);
        verify(userRepository).increasePoint(20L, 1_349L);
        assertThat(firstWinner.getStatus()).isEqualTo(UserBetStatus.WON);
        assertThat(secondWinner.getStatus()).isEqualTo(UserBetStatus.WON);
        assertThat(loser.getStatus()).isEqualTo(UserBetStatus.LOST);
    }

    @Test
    void retainsEntirePoolWhenNoBetSelectedWinner() {
        BettingEvent event = settledReadyEvent();
        UserBet firstLoser = userBet(100L, 10L, "team-b", 1_000L);
        UserBet secondLoser = userBet(200L, 20L, "team-b", 2_000L);
        given(eventRepository.findByIdForUpdate(1L)).willReturn(Optional.of(event));
        given(userBetRepository.findAllByBettingEventIdAndStatusForUpdate(
                1L,
                UserBetStatus.PLACED
        )).willReturn(List.of(firstLoser, secondLoser));

        service.settle(1L);

        assertThat(event.getStatus()).isEqualTo(BettingEventStatus.SETTLED);
        assertThat(firstLoser.getStatus()).isEqualTo(UserBetStatus.LOST);
        assertThat(secondLoser.getStatus()).isEqualTo(UserBetStatus.LOST);
        verify(userRepository, never()).increasePoint(any(), any(Long.class));
        verify(transactionRepository, never()).save(any(BetPointTransaction.class));
    }

    @Test
    void ignoresAlreadySettledEvent() {
        BettingEvent event = settledReadyEvent();
        event.settle();
        given(eventRepository.findByIdForUpdate(1L)).willReturn(Optional.of(event));

        service.settle(1L);

        verify(userRepository, never()).increasePoint(any(), any(Long.class));
    }

    private BettingEvent settledReadyEvent() {
        BettingEvent event = BettingEvent.open(
                "match-1",
                1,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 0),
                LocalDateTime.of(2026, 8, 14, 10, 20)
        );
        ReflectionTestUtils.setField(event, "id", 1L);
        event.recordWinner("team-a");
        return event;
    }

    private UserBet userBet(
            Long id,
            Long userId,
            String teamId,
            long amount
    ) {
        UserBet bet = UserBet.place(1L, userId, teamId, amount);
        ReflectionTestUtils.setField(bet, "id", id);
        return bet;
    }
}
