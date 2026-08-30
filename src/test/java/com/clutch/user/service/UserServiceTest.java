package com.clutch.user.service;

import com.clutch.betting.domain.BetPointTransactionType;
import com.clutch.betting.domain.BetPointTransaction;
import com.clutch.betting.domain.UserBet;
import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.repository.BetPointTransactionRepository;
import com.clutch.betting.repository.UserBetRepository;
import com.clutch.common.privacy.PersonalDataMasker;
import com.clutch.user.domain.User;
import com.clutch.user.domain.UserRole;
import com.clutch.user.dto.MyPointRanking;
import com.clutch.user.dto.PointRanking;
import com.clutch.user.dto.PointTransactionHistory;
import com.clutch.user.dto.PointTransactionType;
import com.clutch.user.dto.UserPointSummary;
import com.clutch.user.exception.UserNotFoundException;
import com.clutch.user.repository.UserRepository;
import com.clutch.watch.repository.WatchPointTransactionRepository;
import com.clutch.watch.domain.WatchPointTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserBetRepository userBetRepository = mock(UserBetRepository.class);
    private final BetPointTransactionRepository betPointTransactionRepository = mock(
            BetPointTransactionRepository.class
    );
    private final WatchPointTransactionRepository watchPointTransactionRepository = mock(
            WatchPointTransactionRepository.class
    );
    private final UserService service = new UserService(
            userRepository,
            userBetRepository,
            betPointTransactionRepository,
            watchPointTransactionRepository,
            new PersonalDataMasker()
    );

    @Test
    void returnsCurrentPoint() {
        given(userRepository.findPointById(10L)).willReturn(Optional.of(12_000L));

        long point = service.getPoint(10L);

        assertThat(point).isEqualTo(12_000L);
    }

    @Test
    void rejectsUnknownUser() {
        given(userRepository.findPointById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPoint(99L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("사용자를 찾을 수 없습니다.");
    }

    @Test
    void returnsPointSummaryIncludingLargestWatchOrBetReward() {
        given(userRepository.findPointById(10L)).willReturn(Optional.of(12_450L));
        given(userBetRepository.countByUserId(10L)).willReturn(26L);
        given(userBetRepository.countByUserIdAndStatus(10L, UserBetStatus.WON)).willReturn(15L);
        given(betPointTransactionRepository.findMaxPointDeltaByUserIdAndTransactionType(
                10L,
                BetPointTransactionType.PAYOUT
        )).willReturn(3_600L);
        given(watchPointTransactionRepository.findMaxAwardedPointByUserId(10L)).willReturn(100L);

        UserPointSummary summary = service.getPointSummary(10L);

        assertThat(summary).isEqualTo(new UserPointSummary(12_450L, 26L, 15L, 3_600L));
    }

    @Test
    void returnsMyPointRankingFromUsersWithMorePoints() {
        given(userRepository.findPointById(10L)).willReturn(Optional.of(12_450L));
        given(userRepository.countByRoleAndPointGreaterThan(UserRole.USER, 12_450L))
                .willReturn(23L);

        MyPointRanking ranking = service.getMyPointRanking(10L);

        assertThat(ranking).isEqualTo(new MyPointRanking(12_450L, 24L));
    }

    @Test
    void returnsWatchAndBetPointTransactionsInNewestOrder() {
        WatchPointTransaction watchTransaction = WatchPointTransaction.create(
                10L,
                20L,
                1L,
                30L,
                100L
        );
        ReflectionTestUtils.setField(watchTransaction, "id", 40L);
        ReflectionTestUtils.setField(
                watchTransaction,
                "createdAt",
                LocalDateTime.of(2026, 8, 31, 11, 0)
        );
        UserBet userBet = UserBet.place(50L, 10L, "team-a", 3_000L);
        ReflectionTestUtils.setField(userBet, "id", 60L);
        BetPointTransaction betTransaction = BetPointTransaction.stake(60L, 3_000L);
        ReflectionTestUtils.setField(betTransaction, "id", 70L);
        ReflectionTestUtils.setField(
                betTransaction,
                "createdAt",
                LocalDateTime.of(2026, 8, 31, 12, 0)
        );
        given(userRepository.findPointById(10L)).willReturn(Optional.of(12_000L));
        given(watchPointTransactionRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(10L))
                .willReturn(List.of(watchTransaction));
        given(userBetRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(10L))
                .willReturn(List.of(userBet));
        given(betPointTransactionRepository.findAllByUserBetIdIn(List.of(60L)))
                .willReturn(List.of(betTransaction));

        List<PointTransactionHistory> history = service.getPointTransactionHistory(10L);

        assertThat(history).containsExactly(
                new PointTransactionHistory(
                        "bet-70",
                        PointTransactionType.BET_STAKE,
                        -3_000L,
                        LocalDateTime.of(2026, 8, 31, 12, 0)
                ),
                new PointTransactionHistory(
                        "watch-40",
                        PointTransactionType.WATCH_REWARD,
                        100L,
                        LocalDateTime.of(2026, 8, 31, 11, 0)
                )
        );
    }

    @Test
    void returnsTopTenPointRankingsWithMaskedNames() {
        User first = User.create(UserRole.USER, "first@example.com", "김현정", "01012345678");
        first.changePoint(48_200L);
        User second = User.create(UserRole.USER, "second@example.com", "이준", "01087654321");
        second.changePoint(41_500L);
        given(userRepository.findAllByRoleOrderByPointDescIdAsc(
                org.mockito.ArgumentMatchers.eq(UserRole.USER),
                any(Pageable.class)
        ))
                .willReturn(List.of(first, second));

        List<PointRanking> rankings = service.getPointRankings();

        assertThat(rankings).containsExactly(
                new PointRanking(1, "김*정", 48_200L),
                new PointRanking(2, "이*", 41_500L)
        );
    }
}
