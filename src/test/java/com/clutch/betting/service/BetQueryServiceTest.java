package com.clutch.betting.service;

import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.domain.UserBet;
import com.clutch.betting.integration.lolesports.LiveBettingCache;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.betting.repository.UserBetRepository;
import com.clutch.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class BetQueryServiceTest {

    private final BettingEventRepository eventRepository = mock(BettingEventRepository.class);
    private final UserBetRepository userBetRepository = mock(UserBetRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final LiveBettingCache liveBettingCache = mock(LiveBettingCache.class);
    private final BetQueryService service = new BetQueryService(
            eventRepository,
            userBetRepository,
            userRepository,
            liveBettingCache,
            Clock.fixed(Instant.parse("2026-08-14T10:01:00Z"), ZoneOffset.UTC)
    );

    @Test
    void returnsCurrentEventWithRemainingTime() {
        BettingEvent event = BettingEvent.open(
                "match-1",
                1,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 0)
        );
        ReflectionTestUtils.setField(event, "id", 1L);
        event.attachGame(
                "game-1",
                LocalDateTime.of(2026, 8, 14, 10, 0),
                Duration.ofMinutes(2)
        );
        given(eventRepository.findFirstByExternalMatchIdAndStatusInOrderBySetNumberDesc(
                "match-1",
                List.of(BettingEventStatus.OPEN, BettingEventStatus.CLOSED)
        )).willReturn(Optional.of(event));
        given(userBetRepository.findByBettingEventIdAndUserId(1L, 10L))
                .willReturn(Optional.empty());
        given(liveBettingCache.isAcceptingBets("match-1", "game-1")).willReturn(true);

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
                LocalDateTime.of(2026, 8, 14, 10, 0)
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
        given(liveBettingCache.isAcceptingBets("match-1", null)).willReturn(true);

        BettingEventView view = service.getCurrentEvent("match-1", 10L);

        assertThat(view.bettingAvailable()).isFalse();
        assertThat(view.myBet().userBetId()).isEqualTo(20L);
    }
}
