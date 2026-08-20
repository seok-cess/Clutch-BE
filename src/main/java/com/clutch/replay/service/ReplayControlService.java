package com.clutch.replay.service;

import com.clutch.lolesports.service.PollingScheduler;
import com.clutch.lolesports.source.ExternalSourceMode;
import com.clutch.lolesports.source.ExternalSourceState;
import com.clutch.lolesports.repository.EsportsMatchRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;

import java.time.Duration;
import java.util.List;

/** 백엔드 API 요청을 replay 스텁 서버의 제어 요청으로 전달한다. */
@Service
@ConditionalOnProperty(prefix = "replay", name = "enabled", havingValue = "true")
public class ReplayControlService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final WebClient replayControlWebClient;
    private final PollingScheduler pollingScheduler;
    private final ExternalSourceState sourceState;
    private final EsportsMatchRepository esportsMatchRepository;

    public ReplayControlService(
            @Qualifier("replayControlWebClient") WebClient replayControlWebClient,
            PollingScheduler pollingScheduler,
            ExternalSourceState sourceState,
            EsportsMatchRepository esportsMatchRepository
    ) {
        this.replayControlWebClient = replayControlWebClient;
        this.pollingScheduler = pollingScheduler;
        this.sourceState = sourceState;
        this.esportsMatchRepository = esportsMatchRepository;
    }

    public ReplayStartResult start() {
        return sourceState.withReadLock(this::startInStubMode);
    }

    private ReplayStartResult startInStubMode() {
        if (sourceState.mode() != ExternalSourceMode.STUB) {
            throw new ReplaySourceModeException();
        }
        try {
            ReplayServerStartResponse response = replayControlWebClient.post()
                    .uri("/__replay/start")
                    .retrieve()
                    .bodyToMono(ReplayServerStartResponse.class)
                    .block(REQUEST_TIMEOUT);
            if (response == null || response.runId() == null || response.matchId() == null) {
                throw new ReplayControlException("replay 스텁 서버가 새 경기 정보를 반환하지 않았다");
            }
            // 기본 폴링을 기다리지 않고, 프론트가 곧바로 새 경기를 조회할 수 있게 한다.
            pollingScheduler.pollMeta();
            pollingScheduler.pollLiveMatches();
            return new ReplayStartResult(response.runId(), response.matchId(), response.gameIds());
        } catch (WebClientException | IllegalStateException exception) {
            throw new ReplayControlException("replay 스텁 서버에 연결할 수 없다. node replay/replay-server.js 실행 상태를 확인하세요.", exception);
        }
    }

    public ReplayStatusResult status() {
        try {
            ReplayServerStatusResponse response = replayControlWebClient.get()
                    .uri("/__replay/status")
                    .retrieve()
                    .bodyToMono(ReplayServerStatusResponse.class)
                    .block(REQUEST_TIMEOUT);
            if (response == null || response.runId() == null || response.matchId() == null) {
                throw new ReplayControlException("replay 스텁 서버가 재생 위치를 반환하지 않았다");
            }
            return toStatusResult(response);
        } catch (WebClientException | IllegalStateException exception) {
            throw new ReplayControlException("replay 스텁 서버의 재생 위치를 조회할 수 없다.", exception);
        }
    }

    public ReplayStatusResult changeSpeed(double speed) {
        if (speed < 1 || speed > 20) {
            throw new ReplayControlException("배속은 1 이상 20 이하여야 한다");
        }
        try {
            ReplayServerStatusResponse response = replayControlWebClient.post()
                    .uri(uri -> uri.path("/__replay/speed").queryParam("value", speed).build())
                    .retrieve()
                    .bodyToMono(ReplayServerStatusResponse.class)
                    .block(REQUEST_TIMEOUT);
            if (response == null || response.runId() == null || response.matchId() == null) {
                throw new ReplayControlException("replay 스텁 서버가 변경된 배속을 반환하지 않았다");
            }
            return toStatusResult(response);
        } catch (WebClientException | IllegalStateException exception) {
            throw new ReplayControlException("replay 스텁 서버의 배속을 변경할 수 없다.", exception);
        }
    }

    private record ReplayServerStartResponse(String runId, String matchId, List<String> gameIds) {
    }

    private ReplayStatusResult toStatusResult(ReplayServerStatusResponse response) {
        return new ReplayStatusResult(
                response.runId(),
                response.matchId(),
                esportsMatchRepository.findByExternalMatchId(response.matchId())
                        .map(match -> match.getId())
                        .orElse(null),
                response.gameIds(),
                response.elapsedSeconds(),
                response.totalSeconds(),
                response.progressPercent(),
                response.fixtureTime(),
                response.speed()
        );
    }

    private record ReplayServerStatusResponse(
            String runId,
            String matchId,
            List<String> gameIds,
            long elapsedSeconds,
            long totalSeconds,
            double progressPercent,
            String fixtureTime,
            double speed
    ) {
    }
}
