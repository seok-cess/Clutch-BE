package com.clutch.watch.redis;

import com.clutch.watch.service.WatchRewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchAliveExpirationListenerTest {

    private static final long USER_ID = 100L;
    private static final String SESSION_KEY = "session-key";

    @Mock
    private WatchSessionRedisRepository watchSessionRedisRepository;

    @Mock
    private WatchRewardService watchRewardService;

    private WatchAliveExpirationListener listener;

    /**
     * 각 테스트에서 Redis 저장소와 포인트 지급 서비스가 주입된 listener를 생성한다.
     */
    @BeforeEach
    void setUp() {
        listener = new WatchAliveExpirationListener(watchSessionRedisRepository, watchRewardService);
    }

    /**
     * Alive TTL 만료 시 포인트 지급 후 active와 session Redis 상태를 순서대로 정리하는지 검증한다.
     */
    @Test
    void rewardsExpiredSessionAndCleansRedisState() {
        WatchSessionSnapshot snapshot = snapshot();
        when(watchSessionRedisRepository.findSession(SESSION_KEY)).thenReturn(Optional.of(snapshot));

        listener.handleExpiredKey("watch:alive:" + USER_ID + ":" + SESSION_KEY);

        InOrder inOrder = inOrder(watchRewardService, watchSessionRedisRepository);
        inOrder.verify(watchRewardService).settle(snapshot);
        inOrder.verify(watchSessionRedisRepository).deleteActiveIfMatches(USER_ID, SESSION_KEY);
        inOrder.verify(watchSessionRedisRepository).deleteSession(SESSION_KEY);
    }

    /**
     * Active 키가 이미 새 세션을 가리키더라도 조건부 삭제를 호출하고 만료 세션 Hash만 정리하는지 검증한다.
     */
    @Test
    void usesConditionalActiveDeletionForReplacedSession() {
        WatchSessionSnapshot snapshot = snapshot();
        when(watchSessionRedisRepository.findSession(SESSION_KEY)).thenReturn(Optional.of(snapshot));
        when(watchSessionRedisRepository.deleteActiveIfMatches(USER_ID, SESSION_KEY)).thenReturn(false);

        listener.handleExpiredKey("watch:alive:" + USER_ID + ":" + SESSION_KEY);

        verify(watchSessionRedisRepository).deleteActiveIfMatches(USER_ID, SESSION_KEY);
        verify(watchSessionRedisRepository).deleteSession(SESSION_KEY);
    }

    /**
     * Session Hash가 먼저 사라졌으면 포인트 지급과 추가 Redis 삭제를 수행하지 않는지 검증한다.
     */
    @Test
    void ignoresExpirationWhenSessionSnapshotIsMissing() {
        when(watchSessionRedisRepository.findSession(SESSION_KEY)).thenReturn(Optional.empty());

        listener.handleExpiredKey("watch:alive:" + USER_ID + ":" + SESSION_KEY);

        verifyNoInteractions(watchRewardService);
        verify(watchSessionRedisRepository, never()).deleteActiveIfMatches(USER_ID, SESSION_KEY);
        verify(watchSessionRedisRepository, never()).deleteSession(SESSION_KEY);
    }

    /**
     * 포인트 지급에 실패하면 재확인할 session 및 active Redis 상태를 삭제하지 않는지 검증한다.
     */
    @Test
    void preservesRedisStateWhenRewardFails() {
        WatchSessionSnapshot snapshot = snapshot();
        when(watchSessionRedisRepository.findSession(SESSION_KEY)).thenReturn(Optional.of(snapshot));
        when(watchRewardService.settle(snapshot)).thenThrow(new IllegalStateException("포인트 지급 실패"));

        assertThatThrownBy(() -> listener.handleExpiredKey("watch:alive:" + USER_ID + ":" + SESSION_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("포인트 지급 실패");

        verify(watchSessionRedisRepository, never()).deleteActiveIfMatches(USER_ID, SESSION_KEY);
        verify(watchSessionRedisRepository, never()).deleteSession(SESSION_KEY);
    }

    /**
     * Alive 키가 아닌 만료 이벤트와 형식이 잘못된 Alive 키를 무시하는지 검증한다.
     */
    @Test
    void ignoresUnrelatedOrMalformedExpiredKeys() {
        listener.handleExpiredKey("watch:active:100");
        listener.handleExpiredKey("watch:session:session-key");
        listener.handleExpiredKey("watch:alive:not-a-number:session-key");
        listener.handleExpiredKey("watch:alive:100");

        verifyNoInteractions(watchSessionRedisRepository, watchRewardService);
    }

    /**
     * 테스트에 사용할 최종 Redis 시청 세션 상태를 생성한다.
     *
     * @return 60초가 누적된 시청 세션 snapshot
     */
    private WatchSessionSnapshot snapshot() {
        return new WatchSessionSnapshot(
                USER_ID,
                200L,
                SESSION_KEY,
                1_000L,
                61_000L,
                60_000L,
                2L
        );
    }
}
