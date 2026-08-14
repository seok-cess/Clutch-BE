package com.clutch.betting.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 세트 배팅 설정 값을 Spring 구성으로 활성화한다. */
@Configuration
@EnableConfigurationProperties(BettingProperties.class)
public class BettingConfig {

    /** 배팅 설정 활성화를 위한 기본 구성을 생성한다. */
    public BettingConfig() {
    }

    /**
     * 배팅 시간 계산에서 공통으로 사용할 UTC 시계를 제공한다.
     *
     * @return UTC 시스템 시계
     */
    @Bean
    public Clock bettingClock() {
        return Clock.systemUTC();
    }
}
