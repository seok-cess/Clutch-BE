package com.clutch.lolesports.source;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/** 현재 모드에 맞는 esports/live-stats WebClient를 선택한다. */
@Component
public class ExternalSourceRouter {

    private final ExternalSourceState state;
    private final WebClient realEsportsWebClient;
    private final WebClient stubEsportsWebClient;
    private final WebClient realLiveStatsWebClient;
    private final WebClient stubLiveStatsWebClient;

    public ExternalSourceRouter(
            ExternalSourceState state,
            @Qualifier("realEsportsWebClient") WebClient realEsportsWebClient,
            @Qualifier("stubEsportsWebClient") WebClient stubEsportsWebClient,
            @Qualifier("realLiveStatsWebClient") WebClient realLiveStatsWebClient,
            @Qualifier("stubLiveStatsWebClient") WebClient stubLiveStatsWebClient
    ) {
        this.state = state;
        this.realEsportsWebClient = realEsportsWebClient;
        this.stubEsportsWebClient = stubEsportsWebClient;
        this.realLiveStatsWebClient = realLiveStatsWebClient;
        this.stubLiveStatsWebClient = stubLiveStatsWebClient;
    }

    public WebClient esportsClient() {
        return state.mode() == ExternalSourceMode.STUB
                ? stubEsportsWebClient
                : realEsportsWebClient;
    }

    public WebClient liveStatsClient() {
        return state.mode() == ExternalSourceMode.STUB
                ? stubLiveStatsWebClient
                : realLiveStatsWebClient;
    }
}
