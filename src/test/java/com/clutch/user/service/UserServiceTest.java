package com.clutch.user.service;

import com.clutch.betting.domain.BetPointTransactionType;
import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.repository.BetPointTransactionRepository;
import com.clutch.betting.repository.UserBetRepository;
import com.clutch.common.privacy.PersonalDataMasker;
import com.clutch.user.domain.User;
import com.clutch.user.domain.UserRole;
import com.clutch.user.dto.PointRanking;
import com.clutch.user.dto.UserPointSummary;
import com.clutch.user.exception.UserNotFoundException;
import com.clutch.user.repository.UserRepository;
import com.clutch.watch.repository.WatchPointTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

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
