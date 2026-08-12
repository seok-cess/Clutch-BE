package com.clutch.watch.config;

import com.clutch.watch.exception.WatchException;
import com.clutch.watch.exception.WatchError;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WatchRewardPropertiesTest {

    /**
     * 외부 설정이 모두 누락돼도 시청 보상 기능이 정해진 MVP 기본 정책으로 동작하는지 검증한다.
     */
    @Test
    void appliesDefaultsWhenPropertiesAreMissing() {
        WatchRewardProperties properties = new WatchRewardProperties(
                null,
                null,
                null,
                null,
                null,
                null,
                0
        );

        assertThat(properties.heartbeatInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.aliveTtl()).isEqualTo(Duration.ofSeconds(90));
        assertThat(properties.activeTtl()).isEqualTo(Duration.ofSeconds(120));
        assertThat(properties.sessionTtl()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.switchLockTtl()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.maxEligibleInterval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.pointsPerMinute()).isEqualTo(10L);
    }

    /**
     * Alive TTL은 heartbeat 사이에 만료되지 않도록 heartbeat 주기보다 길어야 함을 검증한다.
     */
    @Test
    void rejectsAliveTtlThatIsNotLongerThanHeartbeatInterval() {
        assertThatThrownBy(() -> new WatchRewardProperties(
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                Duration.ofSeconds(120),
                Duration.ofHours(1),
                Duration.ofSeconds(10),
                Duration.ofSeconds(60),
                10
        ))
                .isInstanceOf(WatchException.class)
                .hasMessage("Alive TTL은 heartbeat 주기보다 길어야 합니다.");
    }

    /**
     * Active TTL이 Alive TTL보다 길지 않으면 설정 오류로 거부하는지 검증한다.
     */
    @Test
    void rejectsActiveTtlThatIsNotLongerThanAliveTtl() {
        assertWatchError(() -> properties(Duration.ofSeconds(30), Duration.ofSeconds(90),
                Duration.ofSeconds(90), Duration.ofHours(1)), WatchError.ACTIVE_TTL_NOT_LONGER_THAN_ALIVE);
    }

    /**
     * Session TTL이 Active TTL보다 길지 않으면 설정 오류로 거부하는지 검증한다.
     */
    @Test
    void rejectsSessionTtlThatIsNotLongerThanActiveTtl() {
        assertWatchError(() -> properties(Duration.ofSeconds(30), Duration.ofSeconds(90),
                Duration.ofSeconds(120), Duration.ofSeconds(120)), WatchError.SESSION_TTL_NOT_LONGER_THAN_ACTIVE);
    }

    /**
     * 지정한 주요 TTL을 가진 테스트용 시청 보상 설정을 생성한다.
     *
     * @param heartbeat Heartbeat 주기
     * @param alive Alive TTL
     * @param active Active TTL
     * @param session Session TTL
     * @return 테스트용 시청 보상 설정
     */
    private WatchRewardProperties properties(
            Duration heartbeat,
            Duration alive,
            Duration active,
            Duration session
    ) {
        return new WatchRewardProperties(
                heartbeat, alive, active, session,
                Duration.ofSeconds(10), Duration.ofSeconds(60), 10L
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
