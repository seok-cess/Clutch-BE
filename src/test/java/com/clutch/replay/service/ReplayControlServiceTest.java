package com.clutch.replay.service;

import com.clutch.lolesports.repository.EsportsMatchRepository;
import com.clutch.lolesports.service.PollingScheduler;
import com.clutch.lolesports.source.ExternalSourceMode;
import com.clutch.lolesports.source.ExternalSourceProperties;
import com.clutch.lolesports.source.ExternalSourceState;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class ReplayControlServiceTest {

    @Test
    void 새_replay_run을_시작하면_이전_경기의_폴링_상태를_초기화한_뒤_즉시_다시_조회한다() {
        PollingScheduler pollingScheduler = mock(PollingScheduler.class);
        ReplayControlService service = new ReplayControlService(
                replayStartClient(),
                pollingScheduler,
                stubState(),
                mock(EsportsMatchRepository.class)
        );

        ReplayStartResult result = service.start();

        assertEquals("new-run", result.runId());
        assertEquals(2, result.matches().size());
        assertEquals("replay-new-run-m1", result.matches().getFirst().matchId());
        assertEquals(List.of("replay-new-run-g1"), result.matches().getFirst().gameIds());
        assertEquals("replay-new-run-m2", result.matches().get(1).matchId());
        var order = inOrder(pollingScheduler);
        order.verify(pollingScheduler).resetForExternalSourceChange();
        order.verify(pollingScheduler).pollMeta();
        order.verify(pollingScheduler).pollLiveMatches();
    }

    @Test
    void replay_배속을_바꾸면_이전_시간축_캐시를_초기화한_뒤_즉시_다시_조회한다() {
        PollingScheduler pollingScheduler = mock(PollingScheduler.class);
        ReplayControlService service = new ReplayControlService(
                replaySpeedClient(),
                pollingScheduler,
                stubState(),
                mock(EsportsMatchRepository.class)
        );

        ReplayStatusResult result = service.changeSpeed(20);

        assertEquals(20.0, result.speed());
        var order = inOrder(pollingScheduler);
        order.verify(pollingScheduler).resetForExternalSourceChange();
        order.verify(pollingScheduler).pollMeta();
        order.verify(pollingScheduler).pollLiveMatches();
    }

    private WebClient replayStartClient() {
        return WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"runId\":\"new-run\",\"matches\":[{\"matchId\":\"replay-new-run-m1\","
                                + "\"gameIds\":[\"replay-new-run-g1\"]},{\"matchId\":\"replay-new-run-m2\","
                                + "\"gameIds\":[\"replay-new-run-g2\"]}]}")
                        .build()))
                .build();
    }

    private WebClient replaySpeedClient() {
        return WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"runId\":\"run\",\"matches\":[{\"matchId\":\"replay-run-m1\","
                                + "\"gameIds\":[\"replay-run-g1\"]},{\"matchId\":\"replay-run-m2\","
                                + "\"gameIds\":[\"replay-run-g2\"]}],\"elapsedSeconds\":600,"
                                + "\"totalSeconds\":5340,\"progressPercent\":11.2,"
                                + "\"fixtureTime\":\"2026-08-19T08:00:00Z\",\"speed\":20}")
                        .build()))
                .build();
    }

    private ExternalSourceState stubState() {
        return new ExternalSourceState(new ExternalSourceProperties(
                true, ExternalSourceMode.STUB, "http://stub-esports", "http://stub-live"));
    }
}
