package com.clutch.watch.service;

import com.clutch.watch.config.WatchRewardProperties;
import com.clutch.watch.exception.WatchError;
import com.clutch.watch.exception.WatchException;
import com.clutch.watch.redis.reward.RewardClaimCompletionResult;
import com.clutch.watch.redis.reward.RewardClaimStatus;
import com.clutch.watch.redis.session.WatchSessionRedisRepository;
import com.clutch.watch.dto.WatchPointAwardResult;
import com.clutch.watch.dto.WatchPointClaimResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchPointClaimServiceTest {

    private static final long USER_ID = 100L;
    private static final String SESSION_KEY = "session-key";

    @Mock
    private WatchSessionRedisRepository redisRepository;

    @Mock
    private WatchPointAwardService pointAwardService;

    private WatchPointClaimService service;

    @BeforeEach
    void setUp() {
        service = new WatchPointClaimService(
                redisRepository,
                pointAwardService,
                properties(),
                Clock.systemUTC()
        );
        when(redisRepository.tryAcquireSwitchLock(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.anyString()
        ))
                .thenReturn(true);
    }

    @Test
    void awardsPointAndStartsNextRewardSequence() {
        when(redisRepository.prepareRewardClaim(USER_ID, SESSION_KEY, 1L))
                .thenReturn(RewardClaimStatus.SUCCESS);
        when(pointAwardService.award(USER_ID, SESSION_KEY, 1L, 100L))
                .thenReturn(new WatchPointAwardResult(1L, 100L, 500L));
        when(redisRepository.completeRewardClaim(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(SESSION_KEY),
                org.mockito.ArgumentMatchers.eq(1L),
                anyLong()
        )).thenReturn(new RewardClaimCompletionResult(
                RewardClaimStatus.SUCCESS,
                2L
        ));

        WatchPointClaimResult result = service.claim(USER_ID, SESSION_KEY, 1L);

        assertThat(result.awardedPoint()).isEqualTo(100L);
        assertThat(result.totalPoint()).isEqualTo(500L);
        assertThat(result.nextRewardSequence()).isEqualTo(2L);
        verify(redisRepository).releaseSwitchLock(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    void recoversRepeatedRequestFromExistingTransaction() {
        when(redisRepository.prepareRewardClaim(USER_ID, SESSION_KEY, 1L))
                .thenReturn(RewardClaimStatus.INVALID_REWARD_SEQUENCE);
        when(pointAwardService.findExisting(USER_ID, SESSION_KEY, 1L))
                .thenReturn(new WatchPointAwardResult(1L, 100L, 500L));
        when(redisRepository.completeRewardClaim(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(SESSION_KEY),
                org.mockito.ArgumentMatchers.eq(1L),
                anyLong()
        )).thenReturn(new RewardClaimCompletionResult(
                RewardClaimStatus.ALREADY_COMPLETED,
                2L
        ));

        WatchPointClaimResult result = service.claim(USER_ID, SESSION_KEY, 1L);

        assertThat(result.totalPoint()).isEqualTo(500L);
        assertThat(result.nextRewardSequence()).isEqualTo(2L);
    }

    @Test
    void rejectsClaimBeforeFiveMinutes() {
        when(redisRepository.prepareRewardClaim(USER_ID, SESSION_KEY, 1L))
                .thenReturn(RewardClaimStatus.NOT_CLAIMABLE);

        assertWatchError(
                () -> service.claim(USER_ID, SESSION_KEY, 1L),
                WatchError.REWARD_NOT_CLAIMABLE
        );
    }

    private WatchRewardProperties properties() {
        return new WatchRewardProperties(
                Duration.ofSeconds(30),
                Duration.ofSeconds(90),
                Duration.ofSeconds(120),
                Duration.ofHours(1),
                Duration.ofSeconds(10),
                Duration.ofSeconds(60),
                Duration.ofMinutes(5),
                100L
        );
    }

    private void assertWatchError(Runnable runnable, WatchError expectedError) {
        assertThatThrownBy(runnable::run)
                .isInstanceOfSatisfying(WatchException.class,
                        exception -> assertThat(exception.getError()).isEqualTo(expectedError));
    }
}
