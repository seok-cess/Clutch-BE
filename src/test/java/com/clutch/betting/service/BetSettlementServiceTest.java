package com.clutch.betting.service;

import com.clutch.betting.domain.BetPointTransaction;
import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.domain.UserBet;
import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.repository.BetPointTransactionRepository;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.betting.repository.UserBetRepository;
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
import static org.mockito.Mockito.verify;

class BetSettlementServiceTest {

    private final BettingEventRepository eventRepository = mock(BettingEventRepository.class);
    private final UserBetRepository userBetRepository = mock(UserBetRepository.class);
    private final BetPointTransactionRepository transactionRepository =
            mock(BetPointTransactionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final BetSettlementService service = new BetSettlementService(
            eventRepository,
            userBetRepository,
            transactionRepository,
            userRepository
    );

    @Test
    void paysTwiceTheAmountToWinnerAndSettlesLoser() {
        BettingEvent event = settledReadyEvent();
        UserBet winner = userBet(100L, 10L, "team-a", 1_000L);
        UserBet loser = userBet(200L, 20L, "team-b", 2_000L);
        given(eventRepository.findByIdForUpdate(1L)).willReturn(Optional.of(event));
        given(userBetRepository.findAllByBettingEventIdAndStatusForUpdate(
                1L,
                UserBetStatus.PLACED
        )).willReturn(List.of(winner, loser));
        given(userRepository.increasePoint(10L, 2_000L)).willReturn(1);

        service.settle(1L);

        assertThat(event.getStatus()).isEqualTo(BettingEventStatus.SETTLED);
        assertThat(winner.getStatus()).isEqualTo(UserBetStatus.WON);
        assertThat(loser.getStatus()).isEqualTo(UserBetStatus.LOST);
        verify(userRepository).increasePoint(10L, 2_000L);
        verify(transactionRepository).save(any(BetPointTransaction.class));
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
                LocalDateTime.of(2026, 8, 14, 10, 0)
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
