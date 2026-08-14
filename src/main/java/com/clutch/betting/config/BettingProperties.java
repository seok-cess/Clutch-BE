package com.clutch.betting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "betting")
/** 배팅 마감 시간과 라이브 동기화 주기를 관리한다. */
public record BettingProperties(
        Duration closeAfterSetStart,
        Duration synchronizationInterval
) {

    private static final Duration DEFAULT_CLOSE_AFTER_SET_START = Duration.ofMinutes(2);
    private static final Duration DEFAULT_SYNCHRONIZATION_INTERVAL = Duration.ofSeconds(1);

    /** 누락되거나 양수가 아닌 설정을 운영 기본값으로 정규화한다. */
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

    /** 유효한 양수 기간만 사용하고 나머지는 기본값으로 대체한다. */
    private static Duration positiveOrDefault(Duration value, Duration defaultValue) {
        return value == null || value.isZero() || value.isNegative() ? defaultValue : value;
    }
}
