package com.clutch.betting.service;

import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.domain.UserBet;
import com.clutch.betting.dto.BettingEventView;
import com.clutch.betting.dto.UserBetView;
import com.clutch.betting.live.LiveBettingDataProvider;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class BettingQueryTest {

    private final BettingEventRepository eventRepository = mock(BettingEventRepository.class);
    private final UserBetRepository userBetRepository = mock(UserBetRepository.class);
    private final BetPointTransactionRepository transactionRepository =
            mock(BetPointTransactionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final LiveBettingDataProvider liveBettingDataProvider = mock(LiveBettingDataProvider.class);
    private final BettingService service = new BettingService(
            eventRepository,
            userBetRepository,
            transactionRepository,
            userRepository,
            liveBettingDataProvider,
            Clock.fixed(Instant.parse("2026-08-14T10:01:00Z"), ZoneOffset.UTC)
    );

    @Test
    void returnsCurrentEventWithRemainingTime() {
        BettingEvent event = BettingEvent.open(
                "match-1",
                1,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 0),
                LocalDateTime.of(2026, 8, 14, 10, 2)
        );
        ReflectionTestUtils.setField(event, "id", 1L);
        event.attachGame("game-1");
        given(eventRepository.findFirstByExternalMatchIdAndStatusInOrderBySetNumberDesc(
                "match-1",
                List.of(BettingEventStatus.OPEN, BettingEventStatus.CLOSED)
        )).willReturn(Optional.of(event));
        given(userBetRepository.findByBettingEventIdAndUserId(1L, 10L))
                .willReturn(Optional.empty());
        given(liveBettingDataProvider.isAcceptingBets("match-1", "game-1", 1)).willReturn(true);

        BettingEventView view = service.getCurrentEvent("match-1", 10L);

        assertThat(view.remainingSeconds()).isEqualTo(60L);
        assertThat(view.bettingAvailable()).isTrue();
        assertThat(view.myBet()).isNull();
    }

    @Test
    void existingBetDisablesAdditionalBetting() {
        BettingEvent event = BettingEvent.open(
                "match-1",
                2,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 0),
                LocalDateTime.of(2026, 8, 14, 10, 2)
        );
        ReflectionTestUtils.setField(event, "id", 2L);
        UserBet userBet = UserBet.place(2L, 10L, "team-a", 1_000L);
        ReflectionTestUtils.setField(userBet, "id", 20L);
        given(eventRepository.findFirstByExternalMatchIdAndStatusInOrderBySetNumberDesc(
                "match-1",
                List.of(BettingEventStatus.OPEN, BettingEventStatus.CLOSED)
        )).willReturn(Optional.of(event));
        given(userBetRepository.findByBettingEventIdAndUserId(2L, 10L))
                .willReturn(Optional.of(userBet));
        given(liveBettingDataProvider.isAcceptingBets("match-1", null, 2)).willReturn(true);

        BettingEventView view = service.getCurrentEvent("match-1", 10L);

        assertThat(view.bettingAvailable()).isFalse();
        assertThat(view.myBet().userBetId()).isEqualTo(20L);
    }

    @Test
    void returnsMyBetWithOwnerId() {
        UserBet userBet = UserBet.place(2L, 10L, "team-a", 1_000L);
        ReflectionTestUtils.setField(userBet, "id", 20L);
        given(userBetRepository.findByBettingEventIdAndUserId(2L, 10L))
                .willReturn(Optional.of(userBet));
        given(userRepository.findPointById(10L)).willReturn(Optional.of(9_000L));

        UserBetView view = service.getMyBet(2L, 10L);

        assertThat(view.userBetId()).isEqualTo(20L);
        assertThat(view.userId()).isEqualTo(10L);
    }
}
