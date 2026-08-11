package com.clutch.lolesports.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 의 lolesports.* 프로퍼티 바인딩.
 */
@ConfigurationProperties(prefix = "lolesports")
public record LolesportsProperties(
        String apiKey,
        String esportsApiBaseUrl,
        String liveStatsBaseUrl,
        String locale,
        String leagueId,
        String tournamentId,
        /** livestats startingTime 을 현재보다 몇 초 과거로 잡을지 (하한 30초 — 창 끝이 20초 이상 과거여야 함) */
        long liveStatsLagSeconds,
        /** 화면 재생 시점 = now - 이 값(초). 프레임 버퍼에서 이 시점 프레임을 골라 응답 */
        long displayLagSeconds,
        Poll poll
) {
    public record Poll(
            long liveCheckMs,
            long inGameMs,
            long metaMs,
            long backoffBaseMs,
            long backoffMaxMs
    ) {
    }
}
