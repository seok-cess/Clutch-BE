package com.clutch.betting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 배팅 마감 시간을 관리한다.
 *
 * @param firstSetOpenBeforeStart 첫 세트 공식 시작 전 배팅 오픈 간격
 * @param firstSetCloseAfterStart 모든 세트의 실제 시작 후 배팅 마감 유예 간격
 * @param nextSetBettingDuration 다음 세트 시작 시각을 아직 받지 못했을 때의 안전 마감 간격
 */
@ConfigurationProperties(prefix = "betting")
public record BettingProperties(
        Duration firstSetOpenBeforeStart,
        Duration firstSetCloseAfterStart,
        Duration nextSetBettingDuration
) {

    private static final Duration DEFAULT_FIRST_SET_OPEN_BEFORE_START = Duration.ofMinutes(20);
    private static final Duration DEFAULT_FIRST_SET_CLOSE_AFTER_START = Duration.ofMinutes(1);
    private static final Duration DEFAULT_NEXT_SET_BETTING_DURATION = Duration.ofMinutes(20);

    /**
     * 누락되거나 양수가 아닌 설정을 운영 기본값으로 정규화한다.
     *
     * @param firstSetOpenBeforeStart 첫 세트 공식 시작 전 배팅 오픈 간격
     * @param firstSetCloseAfterStart 모든 세트의 실제 시작 후 배팅 마감 유예 간격
     * @param nextSetBettingDuration 다음 세트 시작 시각을 아직 받지 못했을 때의 안전 마감 간격
     */
    public BettingProperties {
        firstSetOpenBeforeStart = positiveOrDefault(
                firstSetOpenBeforeStart,
                DEFAULT_FIRST_SET_OPEN_BEFORE_START
        );
        firstSetCloseAfterStart = positiveOrDefault(
                firstSetCloseAfterStart,
                DEFAULT_FIRST_SET_CLOSE_AFTER_START
        );
        nextSetBettingDuration = positiveOrDefault(
                nextSetBettingDuration,
                DEFAULT_NEXT_SET_BETTING_DURATION
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
