package com.clutch.watch.config;

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
                0
        );

        assertThat(properties.heartbeatInterval()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.aliveTtl()).isEqualTo(Duration.ofSeconds(90));
        assertThat(properties.activeTtl()).isEqualTo(Duration.ofSeconds(120));
        assertThat(properties.sessionTtl()).isEqualTo(Duration.ofHours(1));
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
                Duration.ofSeconds(60),
                10
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("alive TTL must be longer than the heartbeat interval");
    }
}
