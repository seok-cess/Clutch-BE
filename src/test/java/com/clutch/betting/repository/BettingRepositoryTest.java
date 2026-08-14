package com.clutch.betting.repository;

import com.clutch.betting.domain.BetPointTransaction;
import com.clutch.betting.domain.BetPointTransactionType;
import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.UserBet;
import com.clutch.user.domain.User;
import com.clutch.user.domain.UserRole;
import com.clutch.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BettingRepositoryTest {

    @Autowired
    private BettingEventRepository bettingEventRepository;

    @Autowired
    private UserBetRepository userBetRepository;

    @Autowired
    private BetPointTransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesAndFindsBettingAggregate() {
        User user = userRepository.saveAndFlush(
                User.create(UserRole.USER, "betting-repository@example.com")
        );
        BettingEvent event = bettingEventRepository.saveAndFlush(
                BettingEvent.open(
                        "external-match-1",
                        1,
                        "external-team-a",
                        "external-team-b",
                        LocalDateTime.of(2026, 8, 14, 10, 0)
                )
        );
        UserBet bet = userBetRepository.saveAndFlush(
                UserBet.place(event.getId(), user.getId(), "external-team-a", 1_000L)
        );
        transactionRepository.saveAndFlush(BetPointTransaction.stake(bet.getId(), bet.getAmount()));
        entityManager.clear();

        BettingEvent foundEvent = bettingEventRepository
                .findByExternalMatchIdAndSetNumber("external-match-1", 1)
                .orElseThrow();
        UserBet foundBet = userBetRepository
                .findByBettingEventIdAndUserId(foundEvent.getId(), user.getId())
                .orElseThrow();

        assertThat(foundBet.getSelectedExternalTeamId()).isEqualTo("external-team-a");
        assertThat(transactionRepository.existsByUserBetIdAndTransactionType(
                foundBet.getId(),
                BetPointTransactionType.STAKE
        )).isTrue();
    }

    @Test
    void decreasesPointOnlyWhenBalanceIsEnough() {
        User user = User.create(UserRole.USER, "betting-point-update@example.com");
        user.changePoint(1_500L);
        userRepository.saveAndFlush(user);

        int firstUpdate = userRepository.decreasePointIfEnough(user.getId(), 1_000L);
        int rejectedUpdate = userRepository.decreasePointIfEnough(user.getId(), 1_000L);
        entityManager.clear();

        assertThat(firstUpdate).isEqualTo(1);
        assertThat(rejectedUpdate).isZero();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getPoint()).isEqualTo(500L);
    }
}
