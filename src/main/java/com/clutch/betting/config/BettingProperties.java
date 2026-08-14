package com.clutch.betting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 배팅 마감 시간과 라이브 동기화 주기를 관리한다.
 *
 * @param closeAfterSetStart 세트 시작 후 배팅을 허용할 기간
 * @param synchronizationInterval 라이브 동기화 실행 간격
 */
@ConfigurationProperties(prefix = "betting")
public record BettingProperties(
        Duration closeAfterSetStart,
        Duration synchronizationInterval
) {

    private static final Duration DEFAULT_CLOSE_AFTER_SET_START = Duration.ofMinutes(2);
    private static final Duration DEFAULT_SYNCHRONIZATION_INTERVAL = Duration.ofSeconds(1);

    /**
     * 누락되거나 양수가 아닌 설정을 운영 기본값으로 정규화한다.
     *
     * @param closeAfterSetStart 세트 시작 후 배팅을 허용할 기간
     * @param synchronizationInterval 라이브 동기화 실행 간격
     */
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

    /**
     * 유효한 양수 기간만 사용하고 나머지는 기본값으로 대체한다.
     *
     * @param value 검증할 설정 기간
     * @param defaultValue 유효하지 않을 때 사용할 기본 기간
     * @return 유효한 설정 기간 또는 기본 기간
     */
    private static Duration positiveOrDefault(Duration value, Duration defaultValue) {
        return value == null || value.isZero() || value.isNegative() ? defaultValue : value;
    }
}
