package com.clutch.betting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "betting")
public record BettingProperties(
        Duration closeAfterSetStart,
        Duration synchronizationInterval
) {

    private static final Duration DEFAULT_CLOSE_AFTER_SET_START = Duration.ofMinutes(2);
    private static final Duration DEFAULT_SYNCHRONIZATION_INTERVAL = Duration.ofSeconds(1);

    public BettingProperties {
        closeAfterSetStart = positiveOrDefault(
                closeAfterSetStart,
                DEFAULT_CLOSE_AFTER_SET_START
        );
        synchronizationInterval = positiveOrDefault(
                synchronizationInterval,
                DEFAULT_SYNCHRONIZATION_INTERVAL
        );
    }

    private static Duration positiveOrDefault(Duration value, Duration defaultValue) {
        return value == null || value.isZero() || value.isNegative() ? defaultValue : value;
    }
}
