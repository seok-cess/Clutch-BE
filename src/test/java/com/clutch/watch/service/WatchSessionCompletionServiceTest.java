package com.clutch.watch.service;

import com.clutch.watch.domain.WatchSession;
import com.clutch.watch.domain.WatchSessionStatus;
import com.clutch.watch.exception.WatchError;
import com.clutch.watch.exception.WatchException;
import com.clutch.watch.redis.session.WatchSessionSnapshot;
import com.clutch.watch.repository.WatchSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchSessionCompletionServiceTest {

    private static final long USER_ID = 100L;
    private static final long MATCH_ID = 200L;
    private static final String SESSION_KEY = "session-key";
    private static final LocalDateTime ENTERED_AT = LocalDateTime.of(2026, 8, 13, 12, 0);
    private static final long ENTERED_AT_MILLIS = ENTERED_AT.toInstant(ZoneOffset.UTC).toEpochMilli();

    @Mock
    private WatchSessionRepository watchSessionRepository;

    private WatchSessionCompletionService service;

    @BeforeEach
    void setUp() {
        service = new WatchSessionCompletionService(watchSessionRepository);
    }

    @Test
    void completesClaimableSessionWithoutAwardingPoint() {
        WatchSession watchSession = WatchSession.start(
                SESSION_KEY,
                USER_ID,
                MATCH_ID,
                ENTERED_AT
        );
        when(watchSessionRepository.findBySessionKey(SESSION_KEY))
                .thenReturn(Optional.of(watchSession));

        service.completeWithoutReward(snapshot(300_000L));

        assertThat(watchSession.getStatus()).isEqualTo(WatchSessionStatus.COMPLETED);
        assertThat(watchSession.getEligibleMilliseconds()).isEqualTo(300_000L);
    }

    @Test
    void preservesAlreadyCompletedSessionWhenCompletingAgain() {
        WatchSession watchSession = WatchSession.start(
                SESSION_KEY,
                USER_ID,
                MATCH_ID,
                ENTERED_AT
        );
        watchSession.complete(ENTERED_AT.plusMinutes(5), 300_000L);
        when(watchSessionRepository.findBySessionKey(SESSION_KEY))
                .thenReturn(Optional.of(watchSession));

        service.completeWithoutReward(snapshot(100_000L));

        assertThat(watchSession.getEligibleMilliseconds()).isEqualTo(300_000L);
    }

    @Test
    void rejectsSnapshotWithDifferentOwner() {
        WatchSession watchSession = WatchSession.start(
                SESSION_KEY,
                USER_ID,
                MATCH_ID,
                ENTERED_AT
        );
        when(watchSessionRepository.findBySessionKey(SESSION_KEY))
                .thenReturn(Optional.of(watchSession));

        WatchSessionSnapshot snapshot = new WatchSessionSnapshot(
                999L,
                MATCH_ID,
                SESSION_KEY,
                ENTERED_AT_MILLIS,
                ENTERED_AT_MILLIS,
                0L,
                1L,
                1L
        );

        assertWatchError(() -> service.completeWithoutReward(snapshot), WatchError.REDIS_SESSION_USER_MISMATCH);
    }

    @Test
    void rejectsNegativeEligibleTimeBeforeRepositoryAccess() {
        assertWatchError(() -> service.completeWithoutReward(snapshot(-1L)), WatchError.ELIGIBLE_TIME_NEGATIVE);

        verify(watchSessionRepository, never()).findBySessionKey(SESSION_KEY);
    }

    private WatchSessionSnapshot snapshot(long eligibleMilliseconds) {
        return new WatchSessionSnapshot(
                USER_ID,
                MATCH_ID,
                SESSION_KEY,
                ENTERED_AT_MILLIS,
                ENTERED_AT_MILLIS + Math.max(eligibleMilliseconds, 0L),
                eligibleMilliseconds,
                1L,
                1L
        );
    }

    private void assertWatchError(Runnable runnable, WatchError expectedError) {
        assertThatThrownBy(runnable::run)
                .isInstanceOfSatisfying(WatchException.class,
                        exception -> assertThat(exception.getError()).isEqualTo(expectedError));
    }
}
