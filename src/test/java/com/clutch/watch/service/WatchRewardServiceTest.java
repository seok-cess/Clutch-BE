package com.clutch.watch.service;

import com.clutch.user.domain.User;
import com.clutch.user.domain.UserRole;
import com.clutch.user.repository.UserRepository;
import com.clutch.watch.config.WatchRewardProperties;
import com.clutch.watch.domain.WatchPointTransaction;
import com.clutch.watch.domain.WatchSession;
import com.clutch.watch.domain.WatchSessionStatus;
import com.clutch.watch.exception.WatchException;
import com.clutch.watch.exception.WatchError;
import com.clutch.watch.redis.WatchSessionSnapshot;
import com.clutch.watch.repository.WatchPointTransactionRepository;
import com.clutch.watch.repository.WatchSessionRepository;
import com.clutch.watch.service.dto.WatchRewardResult;
import com.clutch.watch.service.service.WatchRewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchRewardServiceTest {

    private static final long USER_ID = 100L;
    private static final long MATCH_ID = 200L;
    private static final long WATCH_SESSION_ID = 300L;
    private static final String SESSION_KEY = "session-key";
    private static final LocalDateTime ENTERED_AT = LocalDateTime.of(2026, 8, 13, 12, 0);
    private static final long ENTERED_AT_MILLIS = ENTERED_AT.toInstant(ZoneOffset.UTC).toEpochMilli();

    @Mock
    private WatchSessionRepository watchSessionRepository;

    @Mock
    private WatchPointTransactionRepository watchPointTransactionRepository;

    @Mock
    private UserRepository userRepository;

    private WatchRewardService service;

    /**
     * 각 테스트에서 동일한 분당 포인트 정책으로 정산 서비스를 생성한다.
     */
    @BeforeEach
    void setUp() {
        service = new WatchRewardService(
                watchSessionRepository,
                watchPointTransactionRepository,
                userRepository,
                rewardProperties()
        );
    }

    /**
     * 319초 시청시간을 완료된 5분으로 계산하여 사용자에게 50P를 지급하는지 검증한다.
     */
    @Test
    void settlesCompletedMinutesAndAwardsPoint() {
        WatchSession watchSession = watchSession();
        User user = user();
        WatchSessionSnapshot snapshot = snapshot(319_000L);
        when(watchSessionRepository.findBySessionKey(SESSION_KEY)).thenReturn(Optional.of(watchSession));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        WatchRewardResult result = service.settle(snapshot);

        assertThat(result.awardedMinutes()).isEqualTo(5L);
        assertThat(result.awardedPoint()).isEqualTo(50L);
        assertThat(result.newlySettled()).isTrue();
        assertThat(user.getPoint()).isEqualTo(50L);
        assertThat(watchSession.getStatus()).isEqualTo(WatchSessionStatus.COMPLETED);
        assertThat(watchSession.getEligibleMilliseconds()).isEqualTo(319_000L);

        ArgumentCaptor<WatchPointTransaction> captor = ArgumentCaptor.forClass(WatchPointTransaction.class);
        verify(watchPointTransactionRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getWatchSessionId()).isEqualTo(WATCH_SESSION_ID);
        assertThat(captor.getValue().getEsportsMatchId()).isEqualTo(MATCH_ID);
        assertThat(captor.getValue().getAwardedPoint()).isEqualTo(50L);
    }

    /**
     * 완료된 1분 미만의 시청 세션도 0P 거래로 정산 완료되는지 검증한다.
     */
    @Test
    void settlesLessThanOneMinuteWithZeroPoint() {
        WatchSession watchSession = watchSession();
        User user = user();
        when(watchSessionRepository.findBySessionKey(SESSION_KEY)).thenReturn(Optional.of(watchSession));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        WatchRewardResult result = service.settle(snapshot(59_999L));

        assertThat(result.awardedMinutes()).isZero();
        assertThat(result.awardedPoint()).isZero();
        assertThat(user.getPoint()).isZero();
        assertThat(watchSession.getStatus()).isEqualTo(WatchSessionStatus.COMPLETED);
        verify(watchPointTransactionRepository).save(org.mockito.ArgumentMatchers.any(WatchPointTransaction.class));
    }

    /**
     * 이미 완료된 세션은 기존 거래 결과를 반환하고 사용자 포인트를 다시 변경하지 않는지 검증한다.
     */
    @Test
    void returnsExistingSettlementWithoutDuplicatedAward() {
        WatchSession watchSession = watchSession();
        watchSession.complete(ENTERED_AT.plusSeconds(319), 319_000L);
        WatchPointTransaction transaction = WatchPointTransaction.create(
                USER_ID,
                WATCH_SESSION_ID,
                1L,
                MATCH_ID,
                50L
        );
        when(watchSessionRepository.findBySessionKey(SESSION_KEY)).thenReturn(Optional.of(watchSession));
        when(watchPointTransactionRepository.findByWatchSessionIdAndRewardSequence(
                WATCH_SESSION_ID, 1L))
                .thenReturn(Optional.of(transaction));

        WatchRewardResult result = service.settle(snapshot(319_000L));

        assertThat(result.awardedPoint()).isEqualTo(50L);
        assertThat(result.newlySettled()).isFalse();
        verify(userRepository, never()).findById(USER_ID);
        verify(watchPointTransactionRepository, never())
                .save(org.mockito.ArgumentMatchers.any(WatchPointTransaction.class));
    }

    /**
     * Redis snapshot의 사용자 ID가 DB 세션과 다르면 정산을 거부하는지 검증한다.
     */
    @Test
    void rejectsSnapshotWithDifferentUser() {
        WatchSession watchSession = watchSession();
        WatchSessionSnapshot snapshot = new WatchSessionSnapshot(
                999L,
                MATCH_ID,
                SESSION_KEY,
                ENTERED_AT_MILLIS,
                ENTERED_AT_MILLIS + 60_000L,
                60_000L,
                1L
        );
        when(watchSessionRepository.findBySessionKey(SESSION_KEY)).thenReturn(Optional.of(watchSession));

        assertThatThrownBy(() -> service.settle(snapshot))
                .isInstanceOf(WatchException.class)
                .hasMessage("Redis 세션의 사용자 ID가 DB 세션과 일치하지 않습니다.");
    }

    /**
     * 음수 유효 시청시간은 Repository 조회나 포인트 계산 전에 거부하는지 검증한다.
     */
    @Test
    void rejectsNegativeEligibleMilliseconds() {
        WatchSessionSnapshot snapshot = new WatchSessionSnapshot(
                USER_ID,
                MATCH_ID,
                SESSION_KEY,
                ENTERED_AT_MILLIS,
                ENTERED_AT_MILLIS,
                -1L,
                1L
        );

        assertThatThrownBy(() -> service.settle(snapshot))
                .isInstanceOf(WatchException.class)
                .hasMessage("유효 시청시간은 음수일 수 없습니다.");
        verify(watchSessionRepository, never()).findBySessionKey(SESSION_KEY);
    }

    /**
     * 정산 snapshot이 없으면 Repository 접근 전에 거부하는지 검증한다.
     */
    @Test
    void rejectsMissingSnapshot() {
        assertWatchError(() -> service.settle(null), WatchError.REWARD_SNAPSHOT_REQUIRED);
        verify(watchSessionRepository, never()).findBySessionKey(org.mockito.ArgumentMatchers.anyString());
    }

    /**
     * Redis sessionKey에 대응하는 DB 시청 세션이 없으면 정산을 거부하는지 검증한다.
     */
    @Test
    void rejectsMissingWatchSession() {
        when(watchSessionRepository.findBySessionKey(SESSION_KEY)).thenReturn(Optional.empty());

        assertWatchError(() -> service.settle(snapshot(60_000L)), WatchError.WATCH_SESSION_NOT_FOUND);
    }

    /**
     * Redis snapshot의 경기 ID가 DB 세션과 다르면 정산을 거부하는지 검증한다.
     */
    @Test
    void rejectsSnapshotWithDifferentMatch() {
        WatchSession watchSession = watchSession();
        WatchSessionSnapshot snapshot = new WatchSessionSnapshot(
                USER_ID, 999L, SESSION_KEY, ENTERED_AT_MILLIS,
                ENTERED_AT_MILLIS + 60_000L, 60_000L, 1L
        );
        when(watchSessionRepository.findBySessionKey(SESSION_KEY)).thenReturn(Optional.of(watchSession));

        assertWatchError(() -> service.settle(snapshot), WatchError.REDIS_SESSION_MATCH_MISMATCH);
    }

    /**
     * DB 세션의 사용자가 사라졌으면 포인트 거래를 생성하지 않고 정산을 거부하는지 검증한다.
     */
    @Test
    void rejectsMissingUser() {
        when(watchSessionRepository.findBySessionKey(SESSION_KEY)).thenReturn(Optional.of(watchSession()));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertWatchError(() -> service.settle(snapshot(60_000L)), WatchError.USER_NOT_FOUND);
        verify(watchPointTransactionRepository, never())
                .save(org.mockito.ArgumentMatchers.any(WatchPointTransaction.class));
    }

    /**
     * 완료된 세션에 포인트 거래가 없으면 성공한 정산으로 가장하지 않는지 검증한다.
     */
    @Test
    void rejectsCompletedSessionWithoutTransaction() {
        WatchSession watchSession = watchSession();
        watchSession.complete(ENTERED_AT.plusMinutes(1), 60_000L);
        when(watchSessionRepository.findBySessionKey(SESSION_KEY)).thenReturn(Optional.of(watchSession));
        when(watchPointTransactionRepository.findByWatchSessionIdAndRewardSequence(
                WATCH_SESSION_ID, 1L)).thenReturn(Optional.empty());

        assertWatchError(() -> service.settle(snapshot(60_000L)), WatchError.POINT_TRANSACTION_NOT_FOUND);
    }

    /**
     * 분당 포인트 곱셈이 long 범위를 넘으면 포인트 변경 전에 정산을 거부하는지 검증한다.
     */
    @Test
    void rejectsRewardPointOverflow() {
        service = new WatchRewardService(
                watchSessionRepository,
                watchPointTransactionRepository,
                userRepository,
                propertiesWithPoints(Long.MAX_VALUE)
        );
        when(watchSessionRepository.findBySessionKey(SESSION_KEY)).thenReturn(Optional.of(watchSession()));

        assertWatchError(() -> service.settle(snapshot(120_000L)), WatchError.REWARD_POINT_OVERFLOW);
        verify(userRepository, never()).findById(USER_ID);
    }

    /**
     * 기존 사용자 포인트와 지급 포인트 합산이 long 범위를 넘으면 거래 저장을 거부하는지 검증한다.
     */
    @Test
    void rejectsUserPointOverflow() {
        User user = user();
        ReflectionTestUtils.setField(user, "point", Long.MAX_VALUE);
        when(watchSessionRepository.findBySessionKey(SESSION_KEY)).thenReturn(Optional.of(watchSession()));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertWatchError(() -> service.settle(snapshot(60_000L)), WatchError.USER_POINT_OVERFLOW);
        verify(watchPointTransactionRepository, never())
                .save(org.mockito.ArgumentMatchers.any(WatchPointTransaction.class));
    }

    /**
     * 테스트에 사용할 WATCHING 상태의 DB 시청 세션을 생성한다.
     *
     * @return ID가 설정된 시청 세션
     */
    private WatchSession watchSession() {
        WatchSession watchSession = WatchSession.start(SESSION_KEY, USER_ID, MATCH_ID, ENTERED_AT);
        ReflectionTestUtils.setField(watchSession, "id", WATCH_SESSION_ID);
        return watchSession;
    }

    /**
     * 테스트에 사용할 사용자 엔티티를 생성한다.
     *
     * @return ID가 설정된 사용자
     */
    private User user() {
        User user = User.create(UserRole.USER, "settlement@example.com");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    /**
     * 주어진 유효 시청시간을 가진 Redis snapshot을 생성한다.
     *
     * @param eligibleMilliseconds 유효 시청시간(milliseconds)
     * @return 테스트용 Redis 시청 세션 snapshot
     */
    private WatchSessionSnapshot snapshot(long eligibleMilliseconds) {
        return new WatchSessionSnapshot(
                USER_ID,
                MATCH_ID,
                SESSION_KEY,
                ENTERED_AT_MILLIS,
                ENTERED_AT_MILLIS + eligibleMilliseconds,
                eligibleMilliseconds,
                1L
        );
    }

    /**
     * 분당 10P 정책을 가진 테스트용 설정을 생성한다.
     *
     * @return 테스트용 시청 보상 정책
     */
    private WatchRewardProperties rewardProperties() {
        return propertiesWithPoints(10L);
    }

    /**
     * 지정한 분당 포인트를 가진 테스트용 설정을 생성한다.
     *
     * @param pointsPerMinute 분당 지급 포인트
     * @return 테스트용 시청 보상 정책
     */
    private WatchRewardProperties propertiesWithPoints(long pointsPerMinute) {
        return new WatchRewardProperties(
                Duration.ofSeconds(30),
                Duration.ofSeconds(90),
                Duration.ofSeconds(120),
                Duration.ofHours(1),
                Duration.ofSeconds(10),
                Duration.ofSeconds(60),
                pointsPerMinute
        );
    }

    /**
     * 실행 결과가 기대한 Watch 오류인지 검증한다.
     *
     * @param runnable 예외가 발생해야 하는 실행 코드
     * @param expectedError 기대하는 Watch 오류
     */
    private void assertWatchError(Runnable runnable, WatchError expectedError) {
        assertThatThrownBy(runnable::run)
                .isInstanceOfSatisfying(WatchException.class,
                        exception -> assertThat(exception.getError()).isEqualTo(expectedError));
    }
}
