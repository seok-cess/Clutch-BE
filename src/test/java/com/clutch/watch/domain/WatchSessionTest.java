package com.clutch.watch.domain;

import com.clutch.watch.exception.WatchException;
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
}
