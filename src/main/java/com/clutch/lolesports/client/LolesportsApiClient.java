package com.clutch.lolesports.client;

import com.clutch.lolesports.config.LolesportsProperties;
import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.dto.external.ScheduleResponse;
import com.clutch.lolesports.dto.external.StandingsResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * esports-api.lolesports.com/persisted/gw 호출 클라이언트.
 * 폴링 스케줄러 전용이므로 블로킹 호출로 단순화 (타임아웃 10초).
 */
@Component
public class LolesportsApiClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient client;
    private final LolesportsProperties props;

    public LolesportsApiClient(@Qualifier("esportsWebClient") WebClient client, LolesportsProperties props) {
        this.client = client;
        this.props = props;
    }

    /** 일정/결과 */
    public ScheduleResponse getSchedule() {
        return getSchedule(null);
    }

    /**
     * 일정/결과. pageToken 을 주면 과거 구간을 이어서 받는다
     * (응답의 pages.older 토큰 — 상대 전적 표본 확보용).
     */
    public ScheduleResponse getSchedule(String pageToken) {
        return client.get()
                .uri(uri -> {
                    var b = uri.path("/getSchedule")
                            .queryParam("hl", props.locale())
                            .queryParam("leagueId", props.leagueId());
                    if (pageToken != null && !pageToken.isBlank()) {
                        b.queryParam("pageToken", pageToken);
                    }
                    return b.build();
                })
                .retrieve()
                .bodyToMono(ScheduleResponse.class)
                .block(TIMEOUT);
    }

    /** 순위 */
    public StandingsResponse getStandings() {
        return client.get()
                .uri(uri -> uri.path("/getStandings")
                        .queryParam("hl", props.locale())
                        .queryParam("tournamentId", props.tournamentId())
                        .build())
                .retrieve()
                .bodyToMono(StandingsResponse.class)
                .block(TIMEOUT);
    }

    /** 진행중 경기 목록 (전체 리그 대상) — 응답 구조가 getSchedule 과 동일하여 DTO 공용 */
    public ScheduleResponse getLive() {
        return client.get()
                .uri(uri -> uri.path("/getLive")
                        .queryParam("hl", props.locale())
                        .build())
                .retrieve()
                .bodyToMono(ScheduleResponse.class)
                .block(TIMEOUT);
    }

    /** 매치 상세 — match.games[] 에서 gameId 획득 */
    public EventDetailsResponse getEventDetails(String matchId) {
        return client.get()
                .uri(uri -> uri.path("/getEventDetails")
                        .queryParam("hl", props.locale())
                        .queryParam("id", matchId)
                        .build())
                .retrieve()
                .bodyToMono(EventDetailsResponse.class)
                .block(TIMEOUT);
    }
}
