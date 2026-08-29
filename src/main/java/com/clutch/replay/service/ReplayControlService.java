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
    public static final double MAX_REPLAY_SPEED = 60;

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
        // 새 replay run은 외부 데이터 소스 전환과 같은 캐시 경계다.
        // 이전 run의 경기·백오프 상태가 새 run을 덮어쓰지 않도록 쓰기 잠금으로 직렬화한다.
        return sourceState.withWriteLock(this::startInStubMode);
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
            if (response == null || response.runId() == null || !hasMatches(response.matches())) {
                throw new ReplayControlException("replay 스텁 서버가 새 경기 정보를 반환하지 않았다");
            }
            // 새 run의 외부 ID는 이전 run과 다르다. 이전 경기의 캐시·백오프·세트 상태를
            // 비운 뒤 즉시 다시 읽어야 이전 세트의 배팅 마감이 남지 않는다.
            pollingScheduler.resetForExternalSourceChange();
            // 기본 폴링을 기다리지 않고, 프론트가 곧바로 새 경기를 조회할 수 있게 한다.
            pollingScheduler.pollMeta();
            pollingScheduler.pollLiveMatches();
            return new ReplayStartResult(response.runId(), toMatches(response.matches()));
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
            if (response == null || response.runId() == null || !hasMatches(response.matches())) {
                throw new ReplayControlException("replay 스텁 서버가 재생 위치를 반환하지 않았다");
            }
            return toStatusResult(response);
        } catch (WebClientException | IllegalStateException exception) {
            throw new ReplayControlException("replay 스텁 서버의 재생 위치를 조회할 수 없다.", exception);
        }
    }

    public ReplayStatusResult changeSpeed(double speed) {
        if (speed < 1 || speed > MAX_REPLAY_SPEED) {
            throw new ReplayControlException("배속은 1 이상 60 이하여야 한다");
        }
        try {
            ReplayServerStatusResponse response = replayControlWebClient.post()
                    .uri(uri -> uri.path("/__replay/speed").queryParam("value", speed).build())
                    .retrieve()
                    .bodyToMono(ReplayServerStatusResponse.class)
                    .block(REQUEST_TIMEOUT);
            if (response == null || response.runId() == null || !hasMatches(response.matches())) {
                throw new ReplayControlException("replay 스텁 서버가 변경된 배속을 반환하지 않았다");
            }
            refreshStubMetadataAfterSpeedChange();
            return toStatusResult(response);
        } catch (WebClientException | IllegalStateException exception) {
            throw new ReplayControlException("replay 스텁 서버의 배속을 변경할 수 없다.", exception);
        }
    }

    private boolean hasMatches(List<ReplayServerMatch> matches) {
        return matches != null
                && !matches.isEmpty()
                && matches.stream().allMatch(match -> match != null
                && match.matchId() != null
                && !match.matchId().isBlank());
    }

    private List<ReplayMatchResult> toMatches(List<ReplayServerMatch> matches) {
        return matches.stream()
                .map(match -> new ReplayMatchResult(
                        match.matchId(),
                        esportsMatchRepository.findByExternalMatchId(match.matchId())
                                .map(existing -> existing.getId())
                                .orElse(null),
                        match.gameIds()
                ))
                .toList();
    }

    private record ReplayServerStartResponse(String runId, List<ReplayServerMatch> matches) {
    }

    /**
     * 배속 변경으로 달라진 일정 시각은 즉시 다시 읽되, 이미 수집한 인게임 프레임은 보존한다.
     * replay 서버는 전달 완료 프레임의 cursor를 유지하므로 백엔드 프레임 캐시만 지우면
     * 이전 프레임을 다시 받을 수 없어 세트 시작·종료 정보가 유실된다.
     */
    private void refreshStubMetadataAfterSpeedChange() {
        sourceState.withWriteLock(() -> {
            if (sourceState.mode() != ExternalSourceMode.STUB) {
                return null;
            }
            pollingScheduler.pollMeta();
            pollingScheduler.pollLiveMatches();
            return null;
        });
    }

    private ReplayStatusResult toStatusResult(ReplayServerStatusResponse response) {
        return new ReplayStatusResult(
                response.runId(),
                toMatches(response.matches()),
                response.elapsedSeconds(),
                response.totalSeconds(),
                response.progressPercent(),
                response.fixtureTime(),
                response.speed()
        );
    }

    private record ReplayServerStatusResponse(
            String runId,
            List<ReplayServerMatch> matches,
            long elapsedSeconds,
            long totalSeconds,
            double progressPercent,
            String fixtureTime,
            double speed
    ) {
    }

    private record ReplayServerMatch(String matchId, List<String> gameIds) {
    }
}
