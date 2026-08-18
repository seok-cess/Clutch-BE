package com.clutch.watch.service;

import com.clutch.lolesports.entity.EsportsMatch;
import com.clutch.lolesports.repository.EsportsMatchRepository;
import com.clutch.user.repository.UserRepository;
import com.clutch.watch.config.WatchRewardProperties;
import com.clutch.watch.dto.WatchHeartbeatResult;
import com.clutch.watch.dto.WatchRewardState;
import com.clutch.watch.dto.WatchSessionStartResult;
import com.clutch.watch.domain.WatchSession;
import com.clutch.watch.exception.WatchError;
import com.clutch.watch.exception.WatchException;
import com.clutch.watch.redis.heartbeat.HeartbeatProcessingResult;
import com.clutch.watch.redis.heartbeat.HeartbeatResult;
import com.clutch.watch.redis.session.SessionKeyReplacementResult;
import com.clutch.watch.redis.session.WatchSessionRedisRepository;
import com.clutch.watch.redis.session.WatchSessionSnapshot;
import com.clutch.watch.repository.WatchSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchSessionServiceTest {

    private static final long USER_ID = 100L;
    private static final long MATCH_ID = 200L;
    private static final String EXTERNAL_MATCH_ID = "external-match-200";
    private static final String OLD_SESSION_KEY = "old-session-key";

    @Mock
    private UserRepository userRepository;

    @Mock
    private EsportsMatchRepository esportsMatchRepository;

    @Mock
    private WatchSessionRepository watchSessionRepository;

    @Mock
    private WatchSessionRedisRepository watchSessionRedisRepository;

    @Mock
    private WatchSessionCompletionService watchSessionCompletionService;

    @Mock
    private WatchAccrualEligibilityProvider watchAccrualEligibilityProvider;

    @Mock
    private TransactionTemplate transactionTemplate;

    private WatchSessionService service;

    /**
     * 각 테스트에서 동일한 시청 보상 정책으로 시청 세션 서비스를 생성한다.
     */
    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        service = new WatchSessionService(
                userRepository,
                esportsMatchRepository,
                watchSessionRepository,
                watchSessionRedisRepository,
                watchSessionCompletionService,
                watchAccrualEligibilityProvider,
                transactionTemplate,
                rewardProperties(),
                Clock.systemUTC()
        );
    }

    /**
     * 활성 세션이 없는 사용자의 DB 및 Redis 시청 세션을 새로 생성하는지 검증한다.
     */
    @Test
    void startsNewWatchSession() {
        allowSessionStart();
        when(watchSessionRedisRepository.findActiveSessionKey(USER_ID)).thenReturn(Optional.empty());

        WatchSessionStartResult result = service.start(USER_ID, EXTERNAL_MATCH_ID);

        assertThat(result.sessionKey()).isNotBlank();
        assertThat(result.matchId()).isEqualTo(MATCH_ID);
        assertThat(result.heartbeatIntervalSeconds()).isEqualTo(30L);
        assertThat(result.sessionTimeoutSeconds()).isEqualTo(90L);
        assertThat(result.heartbeatSequence()).isZero();

        ArgumentCaptor<WatchSession> sessionCaptor = ArgumentCaptor.forClass(WatchSession.class);
        verify(watchSessionRepository).save(sessionCaptor.capture());
        assertThat(sessionCaptor.getValue().getSessionKey()).isEqualTo(result.sessionKey());
        assertThat(sessionCaptor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(sessionCaptor.getValue().getEsportsMatchId()).isEqualTo(MATCH_ID);

        verify(watchSessionRedisRepository).initialize(
                USER_ID,
                MATCH_ID,
                result.sessionKey(),
                result.enteredAt().toEpochMilli()
        );
        verifyLockReleasedWithOwnerToken();
    }

    /**
     * 세션 변경 트랜잭션이 끝난 뒤에만 전환 lock을 해제하는지 검증한다.
     */
    @Test
    void releasesSwitchLockAfterSessionTransactionCompletes() {
        allowSessionStart();
        when(watchSessionRedisRepository.findActiveSessionKey(USER_ID)).thenReturn(Optional.empty());

        service.start(USER_ID, EXTERNAL_MATCH_ID);

        InOrder inOrder = inOrder(transactionTemplate, watchSessionRedisRepository);
        inOrder.verify(transactionTemplate).execute(any());
        inOrder.verify(watchSessionRedisRepository).releaseSwitchLock(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                anyString()
        );
    }

    /**
     * 다른 경기 입장 시 기존 세션을 미지급 종료하고 새 세션을 생성하는지 검증한다.
     */
    @Test
    void completesExistingSessionBeforeStartingDifferentMatch() {
        allowSessionStart();
        WatchSessionSnapshot snapshot = oldSnapshot();
        when(watchSessionRedisRepository.findActiveSessionKey(USER_ID))
                .thenReturn(Optional.of(OLD_SESSION_KEY));
        when(watchSessionRedisRepository.findSession(OLD_SESSION_KEY))
                .thenReturn(Optional.of(snapshot));

        service.start(USER_ID, EXTERNAL_MATCH_ID);

        InOrder inOrder = inOrder(watchSessionCompletionService, watchSessionRepository, watchSessionRedisRepository);
        inOrder.verify(watchSessionCompletionService).completeWithoutReward(snapshot);
        inOrder.verify(watchSessionRedisRepository).deleteActiveIfMatches(USER_ID, OLD_SESSION_KEY);
        inOrder.verify(watchSessionRedisRepository).deleteAlive(USER_ID, OLD_SESSION_KEY);
        inOrder.verify(watchSessionRedisRepository).deleteSession(OLD_SESSION_KEY);
        inOrder.verify(watchSessionRepository).save(any(WatchSession.class));
        inOrder.verify(watchSessionRedisRepository)
                .initialize(org.mockito.ArgumentMatchers.eq(USER_ID),
                        org.mockito.ArgumentMatchers.eq(MATCH_ID), anyString(), anyLong());
    }

    /**
     * 동일 경기 재입장 시 DB 세션과 누적 상태를 유지하고 sessionKey만 교체하는지 검증한다.
     */
    @Test
    void resumesSameMatchWithNewSessionKey() {
        allowSessionStart();
        WatchSessionSnapshot snapshot = sameMatchSnapshot();
        WatchSession watchSession = WatchSession.start(
                OLD_SESSION_KEY,
                USER_ID,
                MATCH_ID,
                LocalDateTime.of(1970, 1, 1, 0, 0, 1)
        );
        when(watchSessionRedisRepository.findActiveSessionKey(USER_ID))
                .thenReturn(Optional.of(OLD_SESSION_KEY));
        when(watchSessionRedisRepository.findSession(OLD_SESSION_KEY))
                .thenReturn(Optional.of(snapshot));
        when(watchSessionRedisRepository.replaceSessionKey(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(OLD_SESSION_KEY),
                anyString()
        )).thenReturn(SessionKeyReplacementResult.SUCCESS);
        when(watchSessionRepository.findBySessionKey(OLD_SESSION_KEY))
                .thenReturn(Optional.of(watchSession));

        WatchSessionStartResult result = service.start(USER_ID, EXTERNAL_MATCH_ID);

        assertThat(result.sessionKey()).isNotEqualTo(OLD_SESSION_KEY);
        assertThat(result.heartbeatSequence()).isEqualTo(snapshot.sequence());
        assertThat(watchSession.getSessionKey()).isEqualTo(result.sessionKey());
        verify(watchSessionRepository, never()).save(any(WatchSession.class));
        verify(watchSessionCompletionService, never()).completeWithoutReward(any());
        verify(watchSessionRepository).flush();
    }

    /**
     * 다른 요청이 전환 lock을 보유하면 DB 및 Redis 세션을 변경하지 않는지 검증한다.
     */
    @Test
    void rejectsStartWhenAnotherRequestIsSwitchingSession() {
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(esportsMatchRepository.findByExternalMatchId(EXTERNAL_MATCH_ID))
                .thenReturn(Optional.of(inProgressMatch()));
        when(watchSessionRedisRepository.tryAcquireSwitchLock(org.mockito.ArgumentMatchers.eq(USER_ID), anyString()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.start(USER_ID, EXTERNAL_MATCH_ID))
                .isInstanceOf(WatchException.class)
                .hasMessage("시청 세션 전환이 진행 중입니다.");

        verify(watchSessionRedisRepository, never()).findActiveSessionKey(USER_ID);
        verify(watchSessionRedisRepository, never()).releaseSwitchLock(
                org.mockito.ArgumentMatchers.eq(USER_ID), anyString());
        verify(watchSessionRepository, never()).save(any(WatchSession.class));
    }

    /**
     * 존재하지 않는 사용자는 경기와 Redis를 조회하기 전에 입장을 거부하는지 검증한다.
     */
    @Test
    void rejectsStartWhenUserIsMissing() {
        when(userRepository.existsById(USER_ID)).thenReturn(false);

        assertWatchError(() -> service.start(USER_ID, EXTERNAL_MATCH_ID), WatchError.USER_NOT_FOUND);
        verify(esportsMatchRepository, never()).findByExternalMatchId(EXTERNAL_MATCH_ID);
        verify(watchSessionRedisRepository, never())
                .tryAcquireSwitchLock(org.mockito.ArgumentMatchers.eq(USER_ID), anyString());
    }

    /**
     * 존재하지 않는 경기는 전환 lock을 만들기 전에 입장을 거부하는지 검증한다.
     */
    @Test
    void rejectsStartWhenMatchIsMissing() {
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(esportsMatchRepository.findByExternalMatchId(EXTERNAL_MATCH_ID)).thenReturn(Optional.empty());

        assertWatchError(() -> service.start(USER_ID, EXTERNAL_MATCH_ID), WatchError.MATCH_NOT_FOUND);
        verify(watchSessionRedisRepository, never())
                .tryAcquireSwitchLock(org.mockito.ArgumentMatchers.eq(USER_ID), anyString());
    }

    /**
     * Active 키가 가리키는 session Hash가 없으면 새 세션을 만들지 않고 lock을 해제하는지 검증한다.
     */
    @Test
    void rejectsStartWhenActiveSessionStateIsMissing() {
        allowSessionStart();
        when(watchSessionRedisRepository.findActiveSessionKey(USER_ID))
                .thenReturn(Optional.of(OLD_SESSION_KEY));
        when(watchSessionRedisRepository.findSession(OLD_SESSION_KEY)).thenReturn(Optional.empty());

        assertWatchError(() -> service.start(USER_ID, EXTERNAL_MATCH_ID), WatchError.WATCH_SESSION_STATE_MISSING);
        verifyLockReleasedWithOwnerToken();
        verify(watchSessionRepository, never()).save(any(WatchSession.class));
    }

    /**
     * 기존 세션 미지급 종료 중 예외가 발생해도 자신이 획득한 전환 lock을 해제하는지 검증한다.
     */
    @Test
    void releasesSwitchLockWhenSessionCompletionFails() {
        allowSessionStart();
        WatchSessionSnapshot snapshot = oldSnapshot();
        when(watchSessionRedisRepository.findActiveSessionKey(USER_ID))
                .thenReturn(Optional.of(OLD_SESSION_KEY));
        when(watchSessionRedisRepository.findSession(OLD_SESSION_KEY))
                .thenReturn(Optional.of(snapshot));
        doThrow(new WatchException(WatchError.WATCH_SESSION_NOT_FOUND))
                .when(watchSessionCompletionService).completeWithoutReward(snapshot);

        assertThatThrownBy(() -> service.start(USER_ID, EXTERNAL_MATCH_ID))
                .isInstanceOf(WatchException.class)
                .hasMessage("시청 세션을 찾을 수 없습니다.");

        verifyLockReleasedWithOwnerToken();
        verify(watchSessionRepository, never()).save(any(WatchSession.class));
    }

    /**
     * 신규 Redis 상태 초기화에 실패해도 자신이 획득한 전환 lock을 해제하는지 검증한다.
     */
    @Test
    void releasesSwitchLockWhenRedisInitializationFails() {
        allowSessionStart();
        when(watchSessionRedisRepository.findActiveSessionKey(USER_ID)).thenReturn(Optional.empty());
        doThrow(new WatchException(WatchError.HEARTBEAT_RESULT_MISSING))
                .when(watchSessionRedisRepository)
                .initialize(org.mockito.ArgumentMatchers.eq(USER_ID),
                        org.mockito.ArgumentMatchers.eq(MATCH_ID), anyString(), anyLong());

        assertWatchError(() -> service.start(USER_ID, EXTERNAL_MATCH_ID), WatchError.HEARTBEAT_RESULT_MISSING);
        verifyLockReleasedWithOwnerToken();
    }

    /**
     * 진행 중 경기의 heartbeat에 서버 수신 시각을 사용하고 Redis 결과를 반환하는지 검증한다.
     */
    @Test
    void processesHeartbeatWithServerTime() {
        allowHeartbeat(true);
        when(watchSessionRedisRepository.heartbeat(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(OLD_SESSION_KEY),
                org.mockito.ArgumentMatchers.eq(3L),
                anyLong(),
                org.mockito.ArgumentMatchers.eq(true)
        )).thenReturn(new HeartbeatProcessingResult(
                HeartbeatResult.SUCCESS,
                300_000L,
                1L
        ));
        long beforeRequest = System.currentTimeMillis();

        WatchHeartbeatResult result = service.heartbeat(USER_ID, OLD_SESSION_KEY, 3L);

        long afterRequest = System.currentTimeMillis();
        assertThat(result.rewardState()).isEqualTo(WatchRewardState.CLAIMABLE);
        assertThat(result.rewardSequence()).isEqualTo(1L);
        assertThat(result.accumulatedSeconds()).isEqualTo(300L);
        assertThat(result.remainingSeconds()).isZero();
        assertThat(result.rewardPoint()).isEqualTo(100L);
        ArgumentCaptor<Long> serverTime = ArgumentCaptor.forClass(Long.class);
        verify(watchSessionRedisRepository).heartbeat(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(OLD_SESSION_KEY),
                org.mockito.ArgumentMatchers.eq(3L),
                serverTime.capture(),
                org.mockito.ArgumentMatchers.eq(true)
        );
        verify(esportsMatchRepository, never()).findByExternalMatchId(anyString());
        assertThat(serverTime.getValue()).isBetween(beforeRequest, afterRequest);
    }

    @Test
    void returnsAccumulatingStateBeforeFiveMinutes() {
        allowHeartbeat(true);
        when(watchSessionRedisRepository.heartbeat(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(OLD_SESSION_KEY),
                org.mockito.ArgumentMatchers.eq(3L),
                anyLong(),
                org.mockito.ArgumentMatchers.eq(true)
        )).thenReturn(new HeartbeatProcessingResult(
                HeartbeatResult.SUCCESS,
                299_000L,
                1L
        ));

        WatchHeartbeatResult result = service.heartbeat(USER_ID, OLD_SESSION_KEY, 3L);

        assertThat(result.rewardState()).isEqualTo(WatchRewardState.ACCUMULATING);
        assertThat(result.accumulatedSeconds()).isEqualTo(299L);
        assertThat(result.remainingSeconds()).isEqualTo(1L);
        assertThat(result.rewardPoint()).isEqualTo(100L);
    }

    /**
     * 세트가 진행 중이지 않으면 세션은 유지하되 시청시간 상태를 일시정지로 반환하는지 검증한다.
     */
    @Test
    void pausesAccumulationOutsideActiveSet() {
        allowHeartbeat(false);
        when(watchSessionRedisRepository.heartbeat(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(OLD_SESSION_KEY),
                org.mockito.ArgumentMatchers.eq(3L),
                anyLong(),
                org.mockito.ArgumentMatchers.eq(false)
        )).thenReturn(new HeartbeatProcessingResult(
                HeartbeatResult.SUCCESS,
                120_000L,
                1L
        ));

        WatchHeartbeatResult result = service.heartbeat(USER_ID, OLD_SESSION_KEY, 3L);

        assertThat(result.rewardState()).isEqualTo(WatchRewardState.PAUSED);
        assertThat(result.accumulatedSeconds()).isEqualTo(120L);
        assertThat(result.remainingSeconds()).isEqualTo(180L);
    }

    /**
     * Redis Lua가 세션 없음 상태를 반환하면 도메인 오류로 변환하는지 검증한다.
     */
    @Test
    void rejectsHeartbeatWhenRedisSessionIsMissing() {
        when(watchSessionRedisRepository.heartbeat(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(OLD_SESSION_KEY),
                org.mockito.ArgumentMatchers.eq(1L),
                anyLong(),
                org.mockito.ArgumentMatchers.eq(false)
        )).thenReturn(new HeartbeatProcessingResult(
                HeartbeatResult.SESSION_NOT_FOUND,
                0L,
                0L
        ));

        assertWatchError(
                () -> service.heartbeat(USER_ID, OLD_SESSION_KEY, 1L),
                WatchError.WATCH_SESSION_NOT_FOUND
        );
        verify(esportsMatchRepository, never()).findByExternalMatchId(anyString());
        verify(watchSessionRedisRepository)
                .heartbeat(org.mockito.ArgumentMatchers.eq(USER_ID),
                        org.mockito.ArgumentMatchers.eq(OLD_SESSION_KEY),
                        org.mockito.ArgumentMatchers.eq(1L), anyLong(),
                        org.mockito.ArgumentMatchers.eq(false));
    }

    private void allowHeartbeat(boolean canAccumulate) {
        WatchSessionSnapshot snapshot = oldSnapshot();
        when(watchSessionRedisRepository.findSession(OLD_SESSION_KEY))
                .thenReturn(Optional.of(snapshot));
        when(watchAccrualEligibilityProvider.canAccumulate(snapshot.matchId()))
                .thenReturn(canAccumulate);
    }

    /**
     * 정상적인 사용자, 진행 중 경기 및 전환 lock 획득 조건을 설정한다.
     */
    private void allowSessionStart() {
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(esportsMatchRepository.findByExternalMatchId(EXTERNAL_MATCH_ID))
                .thenReturn(Optional.of(inProgressMatch()));
        when(watchSessionRedisRepository.tryAcquireSwitchLock(org.mockito.ArgumentMatchers.eq(USER_ID), anyString()))
                .thenReturn(true);
    }

    /**
     * lock 획득과 해제에 동일한 소유 token을 사용했는지 검증한다.
     */
    private void verifyLockReleasedWithOwnerToken() {
        ArgumentCaptor<String> acquiredToken = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> releasedToken = ArgumentCaptor.forClass(String.class);
        verify(watchSessionRedisRepository).tryAcquireSwitchLock(
                org.mockito.ArgumentMatchers.eq(USER_ID), acquiredToken.capture());
        verify(watchSessionRedisRepository).releaseSwitchLock(
                org.mockito.ArgumentMatchers.eq(USER_ID), releasedToken.capture());
        assertThat(releasedToken.getValue()).isEqualTo(acquiredToken.getValue());
    }

    /**
     * 테스트에 사용할 진행 중 경기 엔티티를 생성한다.
     *
     * @return lifecycle 상태가 inProgress인 경기
     */
    private EsportsMatch inProgressMatch() {
        return matchWithStatus("inProgress");
    }

    /**
     * 주어진 lifecycle 상태의 테스트 경기 엔티티를 생성한다.
     *
     * @param lifecycleStatus 생성할 경기 진행 상태
     * @return 지정한 상태를 가진 경기
     */
    private EsportsMatch matchWithStatus(String lifecycleStatus) {
        EsportsMatch match = new EsportsMatch(
                EXTERNAL_MATCH_ID,
                "league",
                "2026",
                "tournament",
                "block",
                LocalDateTime.of(2026, 8, 13, 12, 0),
                LocalDateTime.of(2026, 8, 13, 12, 0),
                lifecycleStatus,
                3
        );
        ReflectionTestUtils.setField(match, "id", MATCH_ID);
        return match;
    }

    /**
     * 테스트에 사용할 기존 Redis 시청 세션 snapshot을 생성한다.
     *
     * @return 기존 활성 세션의 Redis 상태
     */
    private WatchSessionSnapshot oldSnapshot() {
        return new WatchSessionSnapshot(
                USER_ID,
                999L,
                OLD_SESSION_KEY,
                1_000L,
                61_000L,
                60_000L,
                2L,
                1L
        );
    }

    private WatchSessionSnapshot sameMatchSnapshot() {
        return new WatchSessionSnapshot(
                USER_ID,
                MATCH_ID,
                OLD_SESSION_KEY,
                1_000L,
                61_000L,
                60_000L,
                2L,
                1L
        );
    }

    /**
     * 테스트에 사용할 시청 보상 설정을 생성한다.
     *
     * @return 기본 TTL과 분당 10P 정책을 가진 설정
     */
    private WatchRewardProperties rewardProperties() {
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
