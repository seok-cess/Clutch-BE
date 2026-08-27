package com.clutch.betting.service;

import com.clutch.betting.domain.BetPointTransaction;
import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.domain.UserBet;
import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.dto.BettingEventView;
import com.clutch.betting.dto.MyBetView;
import com.clutch.betting.dto.UserBetView;
import com.clutch.betting.live.BettingLiveStateReader;
import com.clutch.betting.repository.BetPointTransactionRepository;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.betting.repository.UserBetRepository;
import com.clutch.lolesports.service.DataCacheService;
import com.clutch.lolesports.service.PollingScheduler;
import com.clutch.lolesports.service.SetWinnerTracker;
import com.clutch.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class BettingQueryTest {

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
            liveStateReader,
            dataCacheService,
            setWinnerTracker,
            pollingScheduler,
            Clock.fixed(Instant.parse("2026-08-14T10:01:00Z"), ZoneOffset.UTC)
    );

    @Test
    void returnsCurrentEventWithBettingAvailability() {
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
        given(eventRepository.findFirstByExternalMatchIdOrderBySetNumberDesc("match-1"))
                .willReturn(Optional.of(event));
        given(userBetRepository.findByBettingEventIdAndUserId(1L, 10L))
                .willReturn(Optional.empty());
        BettingEventView view = service.getCurrentEvent("match-1", 10L);

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
        given(eventRepository.findFirstByExternalMatchIdOrderBySetNumberDesc("match-1"))
                .willReturn(Optional.of(event));
        given(userBetRepository.findByBettingEventIdAndUserId(2L, 10L))
                .willReturn(Optional.of(userBet));
        given(liveStateReader.isAcceptingBets("match-1", null, 2)).willReturn(true);

        BettingEventView view = service.getCurrentEvent("match-1", 10L);

        assertThat(view.bettingAvailable()).isFalse();
        assertThat(view.myBet().userBetId()).isEqualTo(20L);
    }

    @Test
    void returnsCancelledLatestEventAsUnavailable() {
        BettingEvent event = BettingEvent.open(
                "match-1",
                3,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 0),
                LocalDateTime.of(2026, 8, 14, 10, 2)
        );
        ReflectionTestUtils.setField(event, "id", 3L);
        event.cancel();
        given(eventRepository.findFirstByExternalMatchIdOrderBySetNumberDesc("match-1"))
                .willReturn(Optional.of(event));
        given(userBetRepository.findByBettingEventIdAndUserId(3L, 10L))
                .willReturn(Optional.empty());

        BettingEventView view = service.getCurrentEvent("match-1", 10L);

        assertThat(view.status()).isEqualTo(BettingEventStatus.CANCELLED);
        assertThat(view.bettingAvailable()).isFalse();
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

    @Test
    void returnsMyBetsWithEventDetailsInRepositoryOrder() {
        BettingEvent event = BettingEvent.open(
                "match-1",
                2,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 0),
                LocalDateTime.of(2026, 8, 14, 10, 2)
        );
        ReflectionTestUtils.setField(event, "id", 2L);
        event.attachGame("game-2");
        UserBet userBet = UserBet.place(2L, 10L, "team-a", 2_000L);
        ReflectionTestUtils.setField(userBet, "id", 20L);
        given(userRepository.findPointById(10L)).willReturn(Optional.of(8_000L));
        given(userBetRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(10L))
                .willReturn(List.of(userBet));
        given(eventRepository.findAllById(List.of(2L))).willReturn(List.of(event));
        given(userBetRepository.findAllByBettingEventIdIn(anyCollection())).willReturn(List.of(userBet));
        given(transactionRepository.findAllByUserBetIdIn(List.of(20L))).willReturn(List.of());

        List<MyBetView> views = service.getMyBets(10L);

        assertThat(views).singleElement().satisfies(view -> {
            assertThat(view.externalMatchId()).isEqualTo("match-1");
            assertThat(view.externalGameId()).isEqualTo("game-2");
            assertThat(view.setNumber()).isEqualTo(2);
            assertThat(view.selectedTeamId()).isEqualTo("team-a");
            assertThat(view.amount()).isEqualTo(2_000L);
            assertThat(view.settlementPoint()).isNull();
            assertThat(view.netPointChange()).isNull();
            assertThat(view.payoutMultiplier()).isEqualByComparingTo("0.90");
            assertThat(view.payoutMultiplierConfirmed()).isFalse();
        });
    }

    @Test
    void returnsConfirmedPayoutAndNetPointChangeForWonBet() {
        BettingEvent event = BettingEvent.open(
                "match-1",
                2,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 0),
                LocalDateTime.of(2026, 8, 14, 10, 2)
        );
        ReflectionTestUtils.setField(event, "id", 2L);
        event.recordWinner("team-a");
        event.settle();
        UserBet userBet = UserBet.place(2L, 10L, "team-a", 2_000L);
        ReflectionTestUtils.setField(userBet, "id", 20L);
        userBet.win();

        given(userRepository.findPointById(10L)).willReturn(Optional.of(9_000L));
        given(userBetRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(10L))
                .willReturn(List.of(userBet));
        given(eventRepository.findAllById(List.of(2L))).willReturn(List.of(event));
        given(userBetRepository.findAllByBettingEventIdIn(anyCollection())).willReturn(List.of(userBet));
        given(transactionRepository.findAllByUserBetIdIn(List.of(20L)))
                .willReturn(List.of(BetPointTransaction.payout(20L, 3_000L)));

        List<MyBetView> views = service.getMyBets(10L);

        assertThat(views).singleElement().satisfies(view -> {
            assertThat(view.status()).isEqualTo(UserBetStatus.WON);
            assertThat(view.settlementPoint()).isEqualTo(3_000L);
            assertThat(view.netPointChange()).isEqualTo(1_000L);
            assertThat(view.payoutMultiplier()).isEqualByComparingTo(new BigDecimal("1.50"));
            assertThat(view.payoutMultiplierConfirmed()).isTrue();
        });
    }
}
