package com.clutch.betting.service;

import com.clutch.betting.domain.BetPointTransaction;
import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.domain.BetPointTransactionType;
import com.clutch.betting.domain.UserBet;
import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.repository.BetPointTransactionRepository;
import com.clutch.betting.repository.BettingEventRepository;
import com.clutch.betting.repository.UserBetRepository;
import com.clutch.lolesports.service.PollingScheduler;
import com.clutch.user.domain.User;
import com.clutch.user.domain.UserRole;
import com.clutch.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class BetSettlementIntegrationTest {

    @Autowired
    private BetSettlementProcessor settlementProcessor;

    @Autowired
    private BetRefundProcessor refundProcessor;

    @Autowired
    private BettingEventRepository eventRepository;

    @Autowired
    private UserBetRepository userBetRepository;

    @Autowired
    private BetPointTransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private PollingScheduler pollingScheduler;

    @MockitoBean
    private BettingEventSynchronizationScheduler synchronizationScheduler;

    @MockitoBean
    private BetSettlementScheduler settlementScheduler;

    @MockitoBean
    private BetRefundScheduler refundScheduler;

    @Test
    void settlesPersistedBetsExactlyOnce() {
        User winner = userRepository.save(User.create(UserRole.USER, "settlement-winner@example.com"));
        User loser = userRepository.save(User.create(UserRole.USER, "settlement-loser@example.com"));
        BettingEvent event = BettingEvent.open(
                "settlement-match",
                1,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 0)
        );
        event.recordWinner("team-a");
        eventRepository.saveAndFlush(event);
        UserBet winnerBet = userBetRepository.save(
                UserBet.place(event.getId(), winner.getId(), "team-a", 1_000L)
        );
        UserBet loserBet = userBetRepository.save(
                UserBet.place(event.getId(), loser.getId(), "team-b", 2_000L)
        );
        userBetRepository.flush();
        transactionRepository.saveAllAndFlush(java.util.List.of(
                BetPointTransaction.stake(winnerBet.getId(), winnerBet.getAmount()),
                BetPointTransaction.stake(loserBet.getId(), loserBet.getAmount())
        ));
        entityManager.clear();

        BetSettlementResult firstResult = settlementProcessor.settle(event.getId());
        entityManager.flush();
        entityManager.clear();
        BetSettlementResult secondResult = settlementProcessor.settle(event.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(firstResult.totalPayoutPoint()).isEqualTo(2_000L);
        assertThat(secondResult.alreadyProcessed()).isTrue();
        assertThat(eventRepository.findById(event.getId()).orElseThrow().getStatus())
                .isEqualTo(BettingEventStatus.SETTLED);
        assertThat(userBetRepository.findById(winnerBet.getId()).orElseThrow().getStatus())
                .isEqualTo(UserBetStatus.WON);
        assertThat(userBetRepository.findById(loserBet.getId()).orElseThrow().getStatus())
                .isEqualTo(UserBetStatus.LOST);
        assertThat(userRepository.findById(winner.getId()).orElseThrow().getPoint())
                .isEqualTo(2_000L);
        assertThat(userRepository.findById(loser.getId()).orElseThrow().getPoint())
                .isZero();
    }

    @Test
    void refundsCancelledBetExactlyOnce() {
        User user = User.create(UserRole.USER, "refund-user@example.com");
        user.changePoint(1_000L);
        userRepository.saveAndFlush(user);
        BettingEvent event = BettingEvent.open(
                "refund-match",
                3,
                "team-a",
                "team-b",
                LocalDateTime.of(2026, 8, 14, 10, 0)
        );
        event.cancel();
        eventRepository.saveAndFlush(event);
        assertThat(userRepository.decreasePointIfEnough(user.getId(), 1_000L)).isEqualTo(1);
        UserBet userBet = userBetRepository.saveAndFlush(
                UserBet.place(event.getId(), user.getId(), "team-a", 1_000L)
        );
        transactionRepository.saveAndFlush(
                BetPointTransaction.stake(userBet.getId(), userBet.getAmount())
        );
        entityManager.clear();

        BetRefundResult firstResult = refundProcessor.refund(event.getId());
        entityManager.flush();
        entityManager.clear();
        BetRefundResult secondResult = refundProcessor.refund(event.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(firstResult.totalRefundPoint()).isEqualTo(1_000L);
        assertThat(secondResult.alreadyProcessed()).isTrue();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPoint())
                .isEqualTo(1_000L);
        assertThat(userBetRepository.findById(userBet.getId()).orElseThrow().getStatus())
                .isEqualTo(UserBetStatus.REFUNDED);
        assertThat(transactionRepository.existsByUserBetIdAndTransactionType(
                userBet.getId(),
                BetPointTransactionType.REFUND
        )).isTrue();
    }
}
