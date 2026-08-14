package com.clutch.betting.service;

import com.clutch.betting.domain.BetPointTransaction;
import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.UserBet;
import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.dto.BetRefundResult;
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
import static org.mockito.Mockito.verify;

class BetRefundServiceTest {

    private final BettingEventRepository eventRepository = mock(BettingEventRepository.class);
    private final UserBetRepository userBetRepository = mock(UserBetRepository.class);
    private final BetPointTransactionRepository transactionRepository =
            mock(BetPointTransactionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final BetRefundService service = new BetRefundService(
            eventRepository,
            userBetRepository,
            transactionRepository,
            userRepository
    );

    @Test
    void refundsOriginalStakeExactlyOnce() {
        BettingEvent event = BettingEvent.open(
                "match-1",
                3,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 0)
        );
        event.cancel();
        UserBet userBet = UserBet.place(1L, 10L, "team-a", 1_000L);
        ReflectionTestUtils.setField(userBet, "id", 100L);
        given(eventRepository.findByIdForUpdate(1L)).willReturn(Optional.of(event));
        given(userBetRepository.findAllByBettingEventIdAndStatusForUpdate(
                1L,
                UserBetStatus.PLACED
        )).willReturn(List.of(userBet));
        given(userRepository.increasePoint(10L, 1_000L)).willReturn(1);

        BetRefundResult result = service.refund(1L);

        assertThat(result.totalRefundPoint()).isEqualTo(1_000L);
        assertThat(userBet.getStatus()).isEqualTo(UserBetStatus.REFUNDED);
        verify(userRepository).increasePoint(10L, 1_000L);
        verify(transactionRepository).save(any(BetPointTransaction.class));
    }
}
