package com.clutch.replay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** replay 스텁 서버를 제어하기 위한 로컬 주소. */
@ConfigurationProperties(prefix = "replay")
public record ReplayProperties(String controlBaseUrl) {

    private static final String DEFAULT_CONTROL_BASE_URL = "http://localhost:4000";

    public ReplayProperties {
        if (controlBaseUrl == null || controlBaseUrl.isBlank()) {
            controlBaseUrl = DEFAULT_CONTROL_BASE_URL;
        }
    }
}
