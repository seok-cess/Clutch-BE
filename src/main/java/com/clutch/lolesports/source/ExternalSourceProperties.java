package com.clutch.lolesports.source;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 런타임 외부 API 소스 전환 설정. */
@ConfigurationProperties(prefix = "external-source")
public record ExternalSourceProperties(
        boolean enabled,
        ExternalSourceMode initialMode,
        String stubEsportsApiBaseUrl,
        String stubLiveStatsBaseUrl
) {

    private static final String DEFAULT_STUB_BASE_URL = "http://localhost:4000";

    public ExternalSourceProperties {
        if (initialMode == null) {
            initialMode = ExternalSourceMode.REAL;
        }
        if (stubEsportsApiBaseUrl == null || stubEsportsApiBaseUrl.isBlank()) {
            stubEsportsApiBaseUrl = DEFAULT_STUB_BASE_URL;
        }
        if (stubLiveStatsBaseUrl == null || stubLiveStatsBaseUrl.isBlank()) {
            stubLiveStatsBaseUrl = DEFAULT_STUB_BASE_URL;
        }
    }
}
