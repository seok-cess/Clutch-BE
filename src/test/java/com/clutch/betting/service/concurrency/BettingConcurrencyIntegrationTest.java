package com.clutch.betting.service.concurrency;

import com.clutch.betting.domain.BetPointTransaction;
import com.clutch.betting.domain.BetPointTransactionType;
import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.UserBet;
import com.clutch.betting.exception.BettingErrorCode;
import com.clutch.betting.exception.BettingException;
import com.clutch.betting.live.LiveBettingDataProvider;
import com.clutch.betting.repository.BetPointTransactionRepository;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.betting.repository.UserBetRepository;
import com.clutch.betting.service.BettingService;
import com.clutch.betting.scheduler.BettingScheduler;
import com.clutch.betting.service.BetSettlementService;
import com.clutch.lolesports.service.PollingScheduler;
import com.clutch.user.domain.User;
import com.clutch.user.domain.UserRole;
import com.clutch.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=20")
class BettingConcurrencyIntegrationTest {

    private static final int REQUEST_COUNT = 10;

    @Autowired
    private BettingService bettingService;

    @Autowired
    private BetSettlementService settlementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BettingEventRepository eventRepository;

    @Autowired
    private UserBetRepository userBetRepository;

    @Autowired
    private BetPointTransactionRepository transactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private LiveBettingDataProvider liveBettingDataProvider;

    @MockitoBean
    private PollingScheduler pollingScheduler;

    @MockitoBean
    private BettingScheduler bettingScheduler;

    @BeforeEach
    void setUp() {
        cleanUp();
        given(liveBettingDataProvider.isAcceptingBets(any(), any(), anyInt())).willReturn(true);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void concurrentDuplicateRequestsDeductPointOnce() throws Exception {
        User user = user("bet-concurrency-duplicate@example.com", 5_000L);
        BettingEvent event = event("bet-concurrency-duplicate-match");
        AtomicInteger successes = new AtomicInteger();
        Queue<BettingErrorCode> errors = new ConcurrentLinkedQueue<>();

        runConcurrently(REQUEST_COUNT, () -> {
            try {
                bettingService.place(user.getId(), event.getId(), "team-a", 1_000L);
                successes.incrementAndGet();
            } catch (BettingException exception) {
                errors.add(exception.getErrorCode());
            }
        });

        assertThat(successes).hasValue(1);
        assertThat(errors).hasSize(REQUEST_COUNT - 1)
                .allMatch(error -> error == BettingErrorCode.DUPLICATE_BET);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPoint()).isEqualTo(4_000L);
        UserBet savedBet = userBetRepository
                .findByBettingEventIdAndUserId(event.getId(), user.getId())
                .orElseThrow();
        assertThat(transactionRepository.existsByUserBetIdAndTransactionType(
                savedBet.getId(),
                BetPointTransactionType.STAKE
        )).isTrue();
    }

    @Test
    void concurrentSettlementPaysWinnerOnce() throws Exception {
        User user = user("bet-concurrency-settlement@example.com", 5_000L);
        BettingEvent event = event("bet-concurrency-settlement-match");
        assertThat(userRepository.decreasePointIfEnough(user.getId(), 1_000L)).isEqualTo(1);
        UserBet userBet = userBetRepository.saveAndFlush(
                UserBet.place(event.getId(), user.getId(), "team-a", 1_000L)
        );
        transactionRepository.saveAndFlush(BetPointTransaction.stake(userBet.getId(), 1_000L));
        event.recordWinner("team-a");
        eventRepository.saveAndFlush(event);
        runConcurrently(REQUEST_COUNT, () -> settlementService.settle(event.getId()));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getPoint()).isEqualTo(6_000L);
        assertThat(transactionRepository.findByUserBetIdAndTransactionType(
                userBet.getId(),
                BetPointTransactionType.PAYOUT
        )).isPresent();
    }

    @Test
    void concurrentBetsOnDifferentEventsCannotExceedBalance() throws Exception {
        User user = user("bet-concurrency-balance@example.com", 5_000L);
        BettingEvent firstEvent = event("bet-concurrency-balance-one");
        BettingEvent secondEvent = event("bet-concurrency-balance-two");
        java.util.List<Long> eventIds = java.util.List.of(firstEvent.getId(), secondEvent.getId());
        AtomicInteger requestIndex = new AtomicInteger();
        AtomicInteger successes = new AtomicInteger();
        Queue<BettingErrorCode> errors = new ConcurrentLinkedQueue<>();

        runConcurrently(2, () -> {
            Long eventId = eventIds.get(requestIndex.getAndIncrement());
            try {
                bettingService.place(user.getId(), eventId, "team-a", 4_000L);
                successes.incrementAndGet();
            } catch (BettingException exception) {
                errors.add(exception.getErrorCode());
            }
        });

        assertThat(successes).hasValue(1);
        assertThat(errors).containsExactly(BettingErrorCode.INSUFFICIENT_POINT);
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPoint()).isEqualTo(1_000L);
    }

    private User user(String email, long point) {
        User user = User.create(UserRole.USER, email);
        user.changePoint(point);
        return userRepository.saveAndFlush(user);
    }

    private BettingEvent event(String externalMatchId) {
        BettingEvent event = BettingEvent.open(
                externalMatchId,
                1,
                "team-a",
                "team-b",
                LocalDateTime.now().minusMinutes(1)
        );
        event.attachGame(
                externalMatchId.replace("bet-concurrency-", "") + "-game",
                null,
                java.time.Duration.ofMinutes(2)
        );
        return eventRepository.saveAndFlush(event);
    }

    private void runConcurrently(int count, Runnable action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(count);
        Queue<Throwable> unexpected = new ConcurrentLinkedQueue<>();
        try {
            for (int index = 0; index < count; index++) {
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        action.run();
                    } catch (Throwable throwable) {
                        unexpected.add(throwable);
                    } finally {
                        done.countDown();
                    }
                });
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
            assertThat(unexpected).isEmpty();
        } finally {
            executor.shutdownNow();
        }
    }

    private void cleanUp() {
        jdbcTemplate.update("""
                DELETE tx
                FROM bet_point_transaction tx
                JOIN user_bet bet ON bet.user_bet_id = tx.user_bet_id
                JOIN betting_event event ON event.betting_event_id = bet.betting_event_id
                WHERE event.external_match_id LIKE 'bet-concurrency-%'
                """);
        jdbcTemplate.update("""
                DELETE bet
                FROM user_bet bet
                JOIN betting_event event ON event.betting_event_id = bet.betting_event_id
                WHERE event.external_match_id LIKE 'bet-concurrency-%'
                """);
        jdbcTemplate.update("""
                DELETE FROM betting_event
                WHERE external_match_id LIKE 'bet-concurrency-%'
                """);
        jdbcTemplate.update("""
                DELETE FROM user
                WHERE email LIKE 'bet-concurrency-%'
                """);
    }
}
