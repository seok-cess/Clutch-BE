package com.clutch.betting.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BettingProperties.class)
/** 세트 배팅 설정 값을 Spring 구성으로 활성화한다. */
public class BettingConfig {
}
