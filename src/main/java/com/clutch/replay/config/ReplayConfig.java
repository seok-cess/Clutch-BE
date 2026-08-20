package com.clutch.replay.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;

/** replay profile에서만 스텁 서버 제어 클라이언트를 등록한다. */
@Configuration
@Profile("replay")
@EnableConfigurationProperties(ReplayProperties.class)
public class ReplayConfig {

    @Bean
    public WebClient replayControlWebClient(ReplayProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.controlBaseUrl())
                .build();
    }
}
