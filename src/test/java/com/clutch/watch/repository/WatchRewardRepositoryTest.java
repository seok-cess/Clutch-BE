package com.clutch.watch.repository;

import com.clutch.lolesports.entity.EsportsMatch;
import com.clutch.lolesports.repository.EsportsMatchRepository;
import com.clutch.user.domain.User;
import com.clutch.user.domain.UserRole;
import com.clutch.user.repository.UserRepository;
import com.clutch.watch.domain.WatchPointTransaction;
import com.clutch.watch.domain.WatchSession;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시청 보상 Repository와 실제 DB 매핑을 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WatchRewardRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EsportsMatchRepository esportsMatchRepository;

    @Autowired
    private WatchSessionRepository watchSessionRepository;

    @Autowired
    private WatchPointTransactionRepository watchPointTransactionRepository;

    @Autowired
    private EntityManager entityManager;

    /**
     * 사용자 ID로 조회한 엔티티의 포인트 변경을 저장할 수 있는지 검증한다.
     */
    @Test
    void findsUserByIdAndChangesPoint() {
        User savedUser = userRepository.saveAndFlush(
                User.create(UserRole.USER, "watch-user-lock@example.com")
        );
        Long userId = savedUser.getId();
        entityManager.clear();

        User foundUser = userRepository.findById(userId).orElseThrow();
        foundUser.changePoint(50L);
        userRepository.flush();
        entityManager.clear();

        User updatedUser = userRepository.findById(userId).orElseThrow();
        assertThat(updatedUser.getPoint()).isEqualTo(50L);
    }

    /**
     * 저장한 시청 세션을 세션 키로 조회할 수 있는지 검증한다.
     */
    @Test
    void savesAndFindsWatchSessionBySessionKey() {
        RewardFixture fixture = saveRewardFixture("watch-session-lock@example.com", "session-lock-key");
        entityManager.clear();

        WatchSession foundSession = watchSessionRepository
                .findBySessionKey(fixture.sessionKey())
                .orElseThrow();

        assertThat(foundSession.getId()).isEqualTo(fixture.watchSessionId());
        assertThat(foundSession.getUserId()).isEqualTo(fixture.userId());
        assertThat(foundSession.getEsportsMatchId()).isEqualTo(fixture.esportsMatchId());
    }

    /**
     * 같은 시청 세션의 여러 수령 회차를 저장하고 회차별로 조회할 수 있는지 검증한다.
     */
    @Test
    void savesAndFindsPointTransactionsByRewardSequence() {
        RewardFixture fixture = saveRewardFixture("watch-transaction@example.com", "transaction-session-key");
        WatchPointTransaction firstTransaction = WatchPointTransaction.create(
                fixture.userId(),
                fixture.watchSessionId(),
                1L,
                fixture.esportsMatchId(),
                100L
        );
        WatchPointTransaction secondTransaction = WatchPointTransaction.create(
                fixture.userId(),
                fixture.watchSessionId(),
                2L,
                fixture.esportsMatchId(),
                100L
        );
        watchPointTransactionRepository.saveAllAndFlush(java.util.List.of(
                firstTransaction,
                secondTransaction
        ));
        entityManager.clear();

        boolean exists = watchPointTransactionRepository
                .existsByWatchSessionIdAndRewardSequence(fixture.watchSessionId(), 2L);
        WatchPointTransaction foundTransaction = watchPointTransactionRepository
                .findByWatchSessionIdAndRewardSequence(fixture.watchSessionId(), 2L)
                .orElseThrow();

        assertThat(exists).isTrue();
        assertThat(foundTransaction.getUserId()).isEqualTo(fixture.userId());
        assertThat(foundTransaction.getRewardSequence()).isEqualTo(2L);
        assertThat(foundTransaction.getEsportsMatchId()).isEqualTo(fixture.esportsMatchId());
        assertThat(foundTransaction.getAwardedPoint()).isEqualTo(100L);
    }

    private RewardFixture saveRewardFixture(String email, String sessionKey) {
        User user = userRepository.saveAndFlush(User.create(UserRole.USER, email));
        EsportsMatch esportsMatch = esportsMatchRepository.saveAndFlush(createEsportsMatch(sessionKey));
        WatchSession watchSession = watchSessionRepository.saveAndFlush(
                WatchSession.start(
                        sessionKey,
                        user.getId(),
                        esportsMatch.getId(),
                        LocalDateTime.of(2026, 8, 12, 12, 0)
                )
        );
        return new RewardFixture(user.getId(), esportsMatch.getId(), watchSession.getId(), sessionKey);
    }

    private EsportsMatch createEsportsMatch(String uniqueKey) {
        return new EsportsMatch(
                uniqueKey,
                "98767991310872058",
                "2026",
                "tournament-2026",
                "week-1",
                LocalDateTime.of(2026, 8, 12, 11, 0),
                LocalDateTime.of(2026, 8, 12, 12, 0),
                "inProgress",
                3
        );
    }

    private record RewardFixture(
            Long userId,
            Long esportsMatchId,
            Long watchSessionId,
            String sessionKey
    ) {
    }
}
