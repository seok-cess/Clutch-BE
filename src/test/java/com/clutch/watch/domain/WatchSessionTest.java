package com.clutch.watch.domain;

import com.clutch.watch.exception.WatchException;
import com.clutch.watch.exception.WatchError;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WatchSessionTest {

    /**
     * 신규 세션이 입장 시각을 마지막 확인 시각으로 사용하고 WATCHING 상태로 시작하는지 검증한다.
     */
    @Test
    void startsWatchingSession() {
        LocalDateTime enteredAt = LocalDateTime.of(2026, 8, 12, 12, 0);

        WatchSession session = WatchSession.start("session-key", 100L, 200L, enteredAt);

        assertThat(session.getSessionKey()).isEqualTo("session-key");
        assertThat(session.getUserId()).isEqualTo(100L);
        assertThat(session.getEsportsMatchId()).isEqualTo(200L);
        assertThat(session.getEnteredAt()).isEqualTo(enteredAt);
        assertThat(session.getLastSeenAt()).isEqualTo(enteredAt);
        assertThat(session.getEligibleMilliseconds()).isZero();
        assertThat(session.getStatus()).isEqualTo(WatchSessionStatus.WATCHING);
    }

    /**
     * 시청 중인 동일 경기 세션의 외부 키를 최신 화면용 키로 교체하는지 검증한다.
     */
    @Test
    void replacesSessionKeyWhileWatching() {
        WatchSession session = WatchSession.start(
                "old-session-key",
                100L,
                200L,
                LocalDateTime.of(2026, 8, 12, 12, 0)
        );

        session.replaceSessionKey("new-session-key");

        assertThat(session.getSessionKey()).isEqualTo("new-session-key");
    }

    /**
     * Redis에서 확정한 시청시간을 반영하면 세션이 COMPLETED 상태로 전이되는지 검증한다.
     */
    @Test
    void completesWatchingSession() {
        LocalDateTime enteredAt = LocalDateTime.of(2026, 8, 12, 12, 0);
        LocalDateTime lastSeenAt = enteredAt.plusSeconds(319);
        WatchSession session = WatchSession.start("session-key", 100L, 200L, enteredAt);

        session.complete(lastSeenAt, 319_000L);

        assertThat(session.getLastSeenAt()).isEqualTo(lastSeenAt);
        assertThat(session.getEligibleMilliseconds()).isEqualTo(319_000L);
        assertThat(session.getStatus()).isEqualTo(WatchSessionStatus.COMPLETED);
    }

    /**
     * 이미 완료된 동일 세션을 다시 완료하여 중복 정산 상태로 변경하는 것을 거부하는지 검증한다.
     */
    @Test
    void rejectsRepeatedCompletion() {
        LocalDateTime enteredAt = LocalDateTime.of(2026, 8, 12, 12, 0);
        WatchSession session = WatchSession.start("session-key", 100L, 200L, enteredAt);
        session.complete(enteredAt.plusMinutes(1), 60_000L);

        assertThatThrownBy(() -> session.complete(enteredAt.plusMinutes(2), 120_000L))
                .isInstanceOf(WatchException.class)
                .hasMessage("이미 완료된 시청 세션입니다.");
    }

    /**
     * 세션 생성에 필요한 식별자와 입장 시각이 누락되면 정의된 오류로 거부하는지 검증한다.
     */
    @Test
    void rejectsMissingRequiredValues() {
        LocalDateTime enteredAt = LocalDateTime.of(2026, 8, 12, 12, 0);

        assertWatchError(() -> WatchSession.start(" ", 100L, 200L, enteredAt), WatchError.SESSION_KEY_REQUIRED);
        assertWatchError(() -> WatchSession.start("session", null, 200L, enteredAt), WatchError.USER_ID_REQUIRED);
        assertWatchError(() -> WatchSession.start("session", 100L, null, enteredAt), WatchError.MATCH_ID_REQUIRED);
        assertWatchError(() -> WatchSession.start("session", 100L, 200L, null), WatchError.ENTERED_AT_REQUIRED);
    }

    /**
     * 완료 시각과 유효 시청시간이 세션 규칙을 위반하면 정의된 오류로 거부하는지 검증한다.
     */
    @Test
    void rejectsInvalidCompletionValues() {
        LocalDateTime enteredAt = LocalDateTime.of(2026, 8, 12, 12, 0);
        WatchSession session = WatchSession.start("session", 100L, 200L, enteredAt);

        assertWatchError(() -> session.complete(null, 0L), WatchError.LAST_SEEN_AT_REQUIRED);
        assertWatchError(() -> session.complete(enteredAt.minusNanos(1), 0L), WatchError.LAST_SEEN_BEFORE_ENTERED_AT);
        assertWatchError(() -> session.complete(enteredAt, -1L), WatchError.ELIGIBLE_TIME_NEGATIVE);
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
