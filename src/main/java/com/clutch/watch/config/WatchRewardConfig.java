package com.clutch.watch.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 시청 보상 모듈 설정.
 */
@Configuration
@EnableConfigurationProperties(WatchRewardProperties.class)
public class WatchRewardConfig {
}
