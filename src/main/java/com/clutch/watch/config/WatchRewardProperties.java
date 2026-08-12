package com.clutch.watch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 경기 시청시간 기반 포인트 지급 정책 설정.
 */
@ConfigurationProperties(prefix = "watch.reward")
public record WatchRewardProperties(
        Duration heartbeatInterval,
        Duration aliveTtl,
        Duration activeTtl,
        Duration sessionTtl,
        Duration switchLockTtl,
        Duration maxEligibleInterval,
        long pointsPerMinute
) {

    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    private static final Duration DEFAULT_ALIVE_TTL = Duration.ofSeconds(90);
    private static final Duration DEFAULT_ACTIVE_TTL = Duration.ofSeconds(120);
    private static final Duration DEFAULT_SESSION_TTL = Duration.ofHours(1);
    private static final Duration DEFAULT_SWITCH_LOCK_TTL = Duration.ofSeconds(10);
    private static final Duration DEFAULT_MAX_ELIGIBLE_INTERVAL = Duration.ofSeconds(60);
    private static final long DEFAULT_POINTS_PER_MINUTE = 10L;

    public WatchRewardProperties {
        heartbeatInterval = orDefault(heartbeatInterval, DEFAULT_HEARTBEAT_INTERVAL);
        aliveTtl = orDefault(aliveTtl, DEFAULT_ALIVE_TTL);
        activeTtl = orDefault(activeTtl, DEFAULT_ACTIVE_TTL);
        sessionTtl = orDefault(sessionTtl, DEFAULT_SESSION_TTL);
        switchLockTtl = orDefault(switchLockTtl, DEFAULT_SWITCH_LOCK_TTL);
        maxEligibleInterval = orDefault(maxEligibleInterval, DEFAULT_MAX_ELIGIBLE_INTERVAL);
        pointsPerMinute = pointsPerMinute > 0 ? pointsPerMinute : DEFAULT_POINTS_PER_MINUTE;

        if (aliveTtl.compareTo(heartbeatInterval) <= 0) {
            throw new IllegalArgumentException("Alive TTL은 heartbeat 주기보다 길어야 합니다.");
        }
        if (activeTtl.compareTo(aliveTtl) <= 0) {
            throw new IllegalArgumentException("Active TTL은 Alive TTL보다 길어야 합니다.");
        }
        if (sessionTtl.compareTo(activeTtl) <= 0) {
            throw new IllegalArgumentException("Session TTL은 Active TTL보다 길어야 합니다.");
        }
    }

    private static Duration orDefault(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }
}
