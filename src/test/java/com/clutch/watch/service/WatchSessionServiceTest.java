package com.clutch.watch.service;

import com.clutch.lolesports.entity.EsportsMatch;
import com.clutch.lolesports.repository.EsportsMatchRepository;
import com.clutch.user.repository.UserRepository;
import com.clutch.watch.config.WatchRewardProperties;
import com.clutch.watch.domain.WatchSession;
import com.clutch.watch.exception.WatchError;
import com.clutch.watch.exception.WatchException;
import com.clutch.watch.redis.HeartbeatProcessingResult;
import com.clutch.watch.redis.HeartbeatResult;
import com.clutch.watch.redis.SessionKeyReplacementResult;
import com.clutch.watch.redis.WatchSessionRedisRepository;
import com.clutch.watch.redis.WatchSessionSnapshot;
import com.clutch.watch.repository.WatchSessionRepository;
import com.clutch.watch.service.dto.WatchHeartbeatResult;
import com.clutch.watch.service.dto.WatchRewardState;
import com.clutch.watch.service.dto.WatchSessionStartResult;
import com.clutch.watch.service.service.WatchRewardService;
import com.clutch.watch.service.service.WatchSessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchSessionServiceTest {

    private static final long USER_ID = 100L;
    private static final long MATCH_ID = 200L;
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
    private WatchRewardService watchRewardService;

    private WatchSessionService service;

    /**
     * 각 테스트에서 동일한 시청 보상 정책으로 시청 세션 서비스를 생성한다.
     */
    @BeforeEach
    void setUp() {
        service = new WatchSessionService(
                userRepository,
                esportsMatchRepository,
                watchSessionRepository,
                watchSessionRedisRepository,
                watchRewardService,
                rewardProperties()
        );
    }

    /**
     * 활성 세션이 없는 사용자의 DB 및 Redis 시청 세션을 새로 생성하는지 검증한다.
     */
    @Test
    void startsNewWatchSession() {
        allowSessionStart();
        when(watchSessionRedisRepository.findActiveSessionKey(USER_ID)).thenReturn(Optional.empty());

        WatchSessionStartResult result = service.start(USER_ID, MATCH_ID);

        assertThat(result.sessionKey()).isNotBlank();
        assertThat(result.matchId()).isEqualTo(MATCH_ID);
        assertThat(result.heartbeatIntervalSeconds()).isEqualTo(30L);
        assertThat(result.sessionTimeoutSeconds()).isEqualTo(90L);
        assertThat(result.heartbeatSequence()).isZero();
        assertThat(result.newlyCreated()).isTrue();

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
     * 다른 경기 입장 시 기존 세션을 미지급 종료하고 새 세션을 생성하는지 검증한다.
     */
    @Test
    void discardsExistingSessionBeforeStartingDifferentMatch() {
        allowSessionStart();
        WatchSessionSnapshot snapshot = oldSnapshot();
        when(watchSessionRedisRepository.findActiveSessionKey(USER_ID))
                .thenReturn(Optional.of(OLD_SESSION_KEY));
        when(watchSessionRedisRepository.findSession(OLD_SESSION_KEY))
                .thenReturn(Optional.of(snapshot));

        service.start(USER_ID, MATCH_ID);

        InOrder inOrder = inOrder(watchRewardService, watchSessionRepository, watchSessionRedisRepository);
        inOrder.verify(watchRewardService).discard(snapshot);
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

        WatchSessionStartResult result = service.start(USER_ID, MATCH_ID);

        assertThat(result.newlyCreated()).isFalse();
        assertThat(result.sessionKey()).isNotEqualTo(OLD_SESSION_KEY);
        assertThat(result.heartbeatSequence()).isEqualTo(snapshot.sequence());
        assertThat(watchSession.getSessionKey()).isEqualTo(result.sessionKey());
        verify(watchSessionRepository, never()).save(any(WatchSession.class));
        verify(watchRewardService, never()).discard(any());
        verify(watchSessionRepository).flush();
    }

    /**
     * 다른 요청이 전환 lock을 보유하면 DB 및 Redis 세션을 변경하지 않는지 검증한다.
     */
    @Test
    void rejectsStartWhenAnotherRequestIsSwitchingSession() {
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(esportsMatchRepository.findById(MATCH_ID)).thenReturn(Optional.of(inProgressMatch()));
        when(watchSessionRedisRepository.tryAcquireSwitchLock(org.mockito.ArgumentMatchers.eq(USER_ID), anyString()))
                .thenReturn(false);

        assertThatThrownBy(() -> service.start(USER_ID, MATCH_ID))
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

        assertWatchError(() -> service.start(USER_ID, MATCH_ID), WatchError.USER_NOT_FOUND);
        verify(esportsMatchRepository, never()).findById(MATCH_ID);
        verify(watchSessionRedisRepository, never())
                .tryAcquireSwitchLock(org.mockito.ArgumentMatchers.eq(USER_ID), anyString());
    }

    /**
     * 존재하지 않는 경기는 전환 lock을 만들기 전에 입장을 거부하는지 검증한다.
     */
    @Test
    void rejectsStartWhenMatchIsMissing() {
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(esportsMatchRepository.findById(MATCH_ID)).thenReturn(Optional.empty());

        assertWatchError(() -> service.start(USER_ID, MATCH_ID), WatchError.MATCH_NOT_FOUND);
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

        assertWatchError(() -> service.start(USER_ID, MATCH_ID), WatchError.WATCH_SESSION_STATE_MISSING);
        verifyLockReleasedWithOwnerToken();
        verify(watchSessionRepository, never()).save(any(WatchSession.class));
    }

    /**
     * 기존 세션 미지급 종료 중 예외가 발생해도 자신이 획득한 전환 lock을 해제하는지 검증한다.
     */
    @Test
    void releasesSwitchLockWhenDiscardFails() {
        allowSessionStart();
        WatchSessionSnapshot snapshot = oldSnapshot();
        when(watchSessionRedisRepository.findActiveSessionKey(USER_ID))
                .thenReturn(Optional.of(OLD_SESSION_KEY));
        when(watchSessionRedisRepository.findSession(OLD_SESSION_KEY))
                .thenReturn(Optional.of(snapshot));
        doThrow(new WatchException(WatchError.WATCH_SESSION_NOT_FOUND))
                .when(watchRewardService).discard(snapshot);

        assertThatThrownBy(() -> service.start(USER_ID, MATCH_ID))
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

        assertWatchError(() -> service.start(USER_ID, MATCH_ID), WatchError.HEARTBEAT_RESULT_MISSING);
        verifyLockReleasedWithOwnerToken();
    }

    /**
     * 진행 중 경기의 heartbeat에 서버 수신 시각을 사용하고 Redis 결과를 반환하는지 검증한다.
     */
    @Test
    void processesHeartbeatWithServerTime() {
        WatchSessionSnapshot snapshot = oldSnapshot();
        when(watchSessionRedisRepository.findSession(OLD_SESSION_KEY))
                .thenReturn(Optional.of(snapshot));
        when(watchSessionRedisRepository.findActiveSessionKey(USER_ID))
                .thenReturn(Optional.of(OLD_SESSION_KEY));
        when(esportsMatchRepository.findById(snapshot.matchId()))
                .thenReturn(Optional.of(inProgressMatch()));
        when(watchSessionRedisRepository.heartbeat(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(OLD_SESSION_KEY),
                org.mockito.ArgumentMatchers.eq(3L),
                anyLong()
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
                serverTime.capture()
        );
        assertThat(serverTime.getValue()).isBetween(beforeRequest, afterRequest);
    }

    @Test
    void returnsAccumulatingStateBeforeFiveMinutes() {
        WatchSessionSnapshot snapshot = oldSnapshot();
        when(watchSessionRedisRepository.findSession(OLD_SESSION_KEY))
                .thenReturn(Optional.of(snapshot));
        when(watchSessionRedisRepository.findActiveSessionKey(USER_ID))
                .thenReturn(Optional.of(OLD_SESSION_KEY));
        when(esportsMatchRepository.findById(snapshot.matchId()))
                .thenReturn(Optional.of(inProgressMatch()));
        when(watchSessionRedisRepository.heartbeat(
                org.mockito.ArgumentMatchers.eq(USER_ID),
                org.mockito.ArgumentMatchers.eq(OLD_SESSION_KEY),
                org.mockito.ArgumentMatchers.eq(3L),
                anyLong()
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
     * Redis session Hash가 없으면 heartbeat를 실행하지 않고 세션 없음 결과를 반환하는지 검증한다.
     */
    @Test
    void rejectsHeartbeatWhenRedisSessionIsMissing() {
        when(watchSessionRedisRepository.findActiveSessionKey(USER_ID)).thenReturn(Optional.empty());
        when(watchSessionRedisRepository.findSession(OLD_SESSION_KEY)).thenReturn(Optional.empty());

        assertWatchError(
                () -> service.heartbeat(USER_ID, OLD_SESSION_KEY, 1L),
                WatchError.WATCH_SESSION_NOT_FOUND
        );
        verify(esportsMatchRepository, never()).findById(anyLong());
        verify(watchSessionRedisRepository, never())
                .heartbeat(anyLong(), anyString(), anyLong(), anyLong());
    }

    /**
     * 경기 상태가 진행 중이 아니면 Redis 시청시간을 갱신하지 않는지 검증한다.
     */
    @Test
    void rejectsHeartbeatWhenMatchIsNotWatchable() {
        WatchSessionSnapshot snapshot = oldSnapshot();
        when(watchSessionRedisRepository.findSession(OLD_SESSION_KEY))
                .thenReturn(Optional.of(snapshot));
        when(watchSessionRedisRepository.findActiveSessionKey(USER_ID))
                .thenReturn(Optional.of(OLD_SESSION_KEY));
        when(esportsMatchRepository.findById(snapshot.matchId()))
                .thenReturn(Optional.of(completedMatch()));

        assertThatThrownBy(() -> service.heartbeat(USER_ID, OLD_SESSION_KEY, 3L))
                .isInstanceOf(WatchException.class)
                .hasMessage("현재 시청 가능한 경기가 아닙니다.");

        verify(watchSessionRedisRepository, never())
                .heartbeat(anyLong(), anyString(), anyLong(), anyLong());
    }

    /**
     * 정상적인 사용자, 진행 중 경기 및 전환 lock 획득 조건을 설정한다.
     */
    private void allowSessionStart() {
        when(userRepository.existsById(USER_ID)).thenReturn(true);
        when(esportsMatchRepository.findById(MATCH_ID)).thenReturn(Optional.of(inProgressMatch()));
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
     * 테스트에 사용할 종료된 경기 엔티티를 생성한다.
     *
     * @return lifecycle 상태가 completed인 경기
     */
    private EsportsMatch completedMatch() {
        return matchWithStatus("completed");
    }

    /**
     * 주어진 lifecycle 상태의 테스트 경기 엔티티를 생성한다.
     *
     * @param lifecycleStatus 생성할 경기 진행 상태
     * @return 지정한 상태를 가진 경기
     */
    private EsportsMatch matchWithStatus(String lifecycleStatus) {
        return new EsportsMatch(
                "external-match",
                "league",
                "2026",
                "tournament",
                "block",
                LocalDateTime.of(2026, 8, 13, 12, 0),
                LocalDateTime.of(2026, 8, 13, 12, 0),
                lifecycleStatus,
                3
        );
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
