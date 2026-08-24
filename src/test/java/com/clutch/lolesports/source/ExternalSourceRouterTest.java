package com.clutch.lolesports.source;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertSame;

class ExternalSourceRouterTest {

    @Test
    void 현재_모드에_따라_각각의_외부_클라이언트를_선택한다() {
        ExternalSourceState state = new ExternalSourceState(new ExternalSourceProperties(
                true, ExternalSourceMode.REAL, "http://stub-esports", "http://stub-live"));
        WebClient realEsports = WebClient.create("http://real-esports");
        WebClient stubEsports = WebClient.create("http://stub-esports");
        WebClient realLive = WebClient.create("http://real-live");
        WebClient stubLive = WebClient.create("http://stub-live");
        ExternalSourceRouter router = new ExternalSourceRouter(
                state, realEsports, stubEsports, realLive, stubLive);

        assertSame(realEsports, router.esportsClient());
        assertSame(realLive, router.liveStatsClient());

        state.withWriteLock(() -> {
            state.changeMode(ExternalSourceMode.STUB);
            return null;
        });

        assertSame(stubEsports, router.esportsClient());
        assertSame(stubLive, router.liveStatsClient());
    }
}
