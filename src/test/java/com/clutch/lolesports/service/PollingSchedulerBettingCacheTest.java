package com.clutch.lolesports.service;

import com.clutch.lolesports.client.LiveStatsClient;
import com.clutch.lolesports.client.LolesportsApiClient;
import com.clutch.lolesports.config.LolesportsProperties;
import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.dto.external.ScheduleResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 라이브 목록과 분리된 배팅 후보 캐시의 예정 경기 선적재 정책을 검증한다. */
class PollingSchedulerBettingCacheTest {

    /**
     * 공식 시작 30분 이내 예정 경기는 배팅 캐시에만 넣고 먼 경기는 제외하는지 검증한다.
     */
    @Test
    void 가까운_예정_경기만_배팅_캐시에_선적재한다() {
        LolesportsApiClient api = mock(LolesportsApiClient.class);
        DataCacheService cache = new DataCacheService();
        SetWinnerTracker setWinnerTracker = mock(SetWinnerTracker.class);
        GamePersistService persistService = mock(GamePersistService.class);
        PollingScheduler scheduler = new PollingScheduler(
                api,
                mock(LiveStatsClient.class),
                cache,
                mock(PentakillDetector.class),
                persistService,
                setWinnerTracker,
                properties()
        );

        Instant now = Instant.now();
        ScheduleResponse.Event nearEvent = event(
                "near-match",
                now.plus(10, ChronoUnit.MINUTES),
                "unstarted"
        );
        ScheduleResponse.Event farEvent = event(
                "far-match",
                now.plus(40, ChronoUnit.MINUTES),
                "unstarted"
        );
        cache.putSchedule(schedule(List.of(nearEvent, farEvent)));
        when(api.getLive()).thenReturn(schedule(List.of()));
        when(api.getEventDetails("near-match")).thenReturn(details("near-match"));

        scheduler.pollLiveMatches();

        assertTrue(cache.getLiveMatches().isEmpty());
        assertEquals(
                List.of("near-match"),
                cache.getBettingMatches().stream()
                        .map(DataCacheService.LiveMatch::matchId)
                        .toList()
        );
        verify(api).getEventDetails("near-match");
    }

    /** 진행 중 매치는 시청 세션 FK가 즉시 참조할 수 있도록 라이브 폴링에서 선저장한다. */
    @Test
    void 라이브_매치를_시청_세션보다_먼저_저장한다() {
        LolesportsApiClient api = mock(LolesportsApiClient.class);
        DataCacheService cache = new DataCacheService();
        GamePersistService persistService = mock(GamePersistService.class);
        PollingScheduler scheduler = new PollingScheduler(
                api,
                mock(LiveStatsClient.class),
                cache,
                mock(PentakillDetector.class),
                persistService,
                mock(SetWinnerTracker.class),
                properties()
        );
        ScheduleResponse.Event liveEvent = event("live-match", Instant.now(), "inProgress");
        when(api.getLive()).thenReturn(schedule(List.of(liveEvent)));
        when(api.getEventDetails("live-match")).thenReturn(details("live-match"));

        scheduler.pollLiveMatches();

        DataCacheService.LiveMatch liveMatch = cache.getLiveMatches().getFirst();
        assertEquals("live-match", liveMatch.matchId());
        verify(persistService).persistLiveMatch(liveMatch);
    }

    /**
     * 테스트용 기본 폴링 설정을 생성한다.
     *
     * @return 기본값으로 정규화된 lolesports 설정
     */
    private LolesportsProperties properties() {
        return new LolesportsProperties(
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                null
        );
    }

    /**
     * 지정 시각과 상태를 가진 일정 이벤트를 생성한다.
     *
     * @param matchId 외부 매치 ID
     * @param startTime 공식 시작 시각
     * @param state 일정 상태
     * @return 두 참가 팀을 가진 일정 이벤트
     */
    private ScheduleResponse.Event event(String matchId, Instant startTime, String state) {
        List<ScheduleResponse.Team> teams = List.of(
                new ScheduleResponse.Team("team-a", "A", "A", null, null, null),
                new ScheduleResponse.Team("team-b", "B", "B", null, null, null)
        );
        return new ScheduleResponse.Event(
                startTime.toString(),
                state,
                "match",
                "week",
                new ScheduleResponse.League("LCK", "lck"),
                new ScheduleResponse.Match(
                        matchId,
                        List.of(),
                        teams,
                        new ScheduleResponse.Strategy("bestOf", 3)
                )
        );
    }

    /**
     * 일정 이벤트 목록을 포함한 API 응답을 생성한다.
     *
     * @param events 일정 이벤트 목록
     * @return getLive 또는 getSchedule 형식의 응답
     */
    private ScheduleResponse schedule(List<ScheduleResponse.Event> events) {
        return new ScheduleResponse(
                new ScheduleResponse.Data(
                        new ScheduleResponse.Schedule(null, events)
                )
        );
    }

    /**
     * 예정 경기의 참가 팀을 보완할 상세 응답을 생성한다.
     *
     * @return 세트가 아직 생성되지 않은 매치 상세 응답
     */
    private EventDetailsResponse details(String matchId) {
        List<ScheduleResponse.Team> teams = List.of(
                new ScheduleResponse.Team("team-a", "A", "A", null, null, null),
                new ScheduleResponse.Team("team-b", "B", "B", null, null, null)
        );
        return new EventDetailsResponse(
                new EventDetailsResponse.Data(
                        new EventDetailsResponse.Event(
                                matchId,
                                "match",
                                new ScheduleResponse.League("LCK", "lck"),
                                new EventDetailsResponse.Match(
                                        teams,
                                        List.of(),
                                        new ScheduleResponse.Strategy("bestOf", 3)
                                )
                        )
                )
        );
    }
}
