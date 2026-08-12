package com.clutch.lolesports.service;

import com.clutch.lolesports.client.LiveStatsClient;
import com.clutch.lolesports.client.LolesportsApiClient;
import com.clutch.lolesports.dto.external.DetailsResponse;
import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.dto.external.WindowResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 과거(종료된) 경기의 인게임 데이터를 온디맨드로 로드한다.
 *
 * 라이브 폴링과 달리 사용자가 특정 매치/게임을 열람할 때 1회만 소스를 호출하고,
 * 결과는 인메모리 캐시에 남겨 이후 요청은 캐시로 응답한다 (종료된 게임 데이터는 불변).
 * 실패한 gameId는 짧게 네거티브 캐시해서 소스를 반복 호출하지 않는다.
 */
@Service
public class HistoricalGameService {

    private static final Logger log = LoggerFactory.getLogger(HistoricalGameService.class);
    /** 로드 실패한 게임 재시도 금지 시간 */
    private static final long FAIL_CACHE_MS = 60_000;
    /**
     * 과거 경기 타임라인 수집 간격(초).
     * livestats 응답 1건이 10초 창을 덮으므로 10초 간격이면 빈틈 없이 전 구간을 채운다.
     */
    private static final long TIMELINE_STEP_SECONDS = 10;
    /** 타임라인 수집 상한 — 10초 x 360 = 60분 (장기전은 그 지점까지만) */
    private static final int TIMELINE_MAX_REQUESTS = 360;
    /** 한 번에 병렬로 던지는 요청 수 (소스 부담 제한) */
    private static final int TIMELINE_BATCH = 12;

    /** 타임라인까지 수집 완료한 게임 */
    private final java.util.Set<String> timelineLoaded = ConcurrentHashMap.newKeySet();

    /** 타임라인 병렬 수집 전용 스레드 풀 — 요청 스레드를 점유하지 않게 분리 */
    private final java.util.concurrent.ExecutorService timelineExecutor =
            java.util.concurrent.Executors.newFixedThreadPool(TIMELINE_BATCH, r -> {
                Thread t = new Thread(r, "timeline-loader");
                t.setDaemon(true);
                return t;
            });

    private final LolesportsApiClient api;
    private final LiveStatsClient liveStats;
    private final DataCacheService cache;

    /** matchId → games[] (모든 게임이 completed 인 매치만 영구 캐시) */
    private final Map<String, List<EventDetailsResponse.Game>> completedMatchGames = new ConcurrentHashMap<>();
    /** gameId → 이 시각까지 재시도 금지 (네거티브 캐시) */
    private final Map<String, Long> failedUntil = new ConcurrentHashMap<>();

    public HistoricalGameService(LolesportsApiClient api, LiveStatsClient liveStats, DataCacheService cache) {
        this.api = api;
        this.liveStats = liveStats;
        this.cache = cache;
    }

    /**
     * 종료된 게임의 전체 타임라인을 훑어 버퍼에 채운다 (골드차 추이 그래프용).
     * livestats 는 한 번에 10초 창만 주므로, 게임 시작부터 10초 간격으로 요청해 이어 붙인다.
     *
     * 순차 호출은 요청당 ~0.4초라 35분 경기에 1.5분이 걸린다. 그래서 배치 단위로 병렬 호출하되,
     * 동시 요청 수를 제한해 소스에 부담을 주지 않는다.
     * 게임당 1회만 수행하며, 종료된 게임은 데이터가 불변이라 이후 요청은 캐시로 응답된다.
     */
    private void loadTimeline(String gameId) {
        if (!timelineLoaded.add(gameId)) {
            return; // 이미 수집함
        }
        java.time.Instant start = cache.getGameStart(gameId);
        if (start == null) {
            timelineLoaded.remove(gameId);
            return;
        }

        long began = System.currentTimeMillis();
        int added = 0;
        boolean finished = false;

        for (int base = 0; base < TIMELINE_MAX_REQUESTS && !finished; base += TIMELINE_BATCH) {
            List<java.util.concurrent.CompletableFuture<WindowResponse>> futures = new ArrayList<>();
            for (int i = base; i < Math.min(base + TIMELINE_BATCH, TIMELINE_MAX_REQUESTS); i++) {
                java.time.Instant at = start.plusSeconds(i * TIMELINE_STEP_SECONDS);
                futures.add(java.util.concurrent.CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return liveStats.getWindowAt(gameId, at);
                            } catch (Exception e) {
                                return null; // 개별 실패는 그 구간만 비운다
                            }
                        }, timelineExecutor));
            }

            boolean emptyRun = true;
            for (java.util.concurrent.CompletableFuture<WindowResponse> f : futures) {
                WindowResponse w;
                try {
                    w = f.get(20, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    continue;
                }
                if (w == null || w.frames() == null || w.frames().isEmpty()) {
                    continue;
                }
                emptyRun = false;
                cache.addWindowFrames(gameId, w.gameMetadata(), w.frames());
                added++;
                WindowResponse.Frame lastFrame = w.frames().get(w.frames().size() - 1);
                if ("finished".equalsIgnoreCase(lastFrame.gameState())) {
                    finished = true;
                }
            }
            // 한 배치가 통째로 비었으면 게임 끝을 지난 것
            if (emptyRun) {
                break;
            }
        }
        log.info("게임 {} 타임라인 수집: {}구간, {}ms", gameId, added, System.currentTimeMillis() - began);
    }

    /** 매치의 팀 목록 (id 포함 — 진영 판별용). getSchedule 에는 id 가 없어 여기서 얻는다. */
    public List<com.clutch.lolesports.dto.external.ScheduleResponse.Team> getTeams(String matchId) {
        try {
            EventDetailsResponse res = api.getEventDetails(matchId);
            if (res == null || res.data() == null || res.data().event() == null
                    || res.data().event().match() == null
                    || res.data().event().match().teams() == null) {
                return List.of();
            }
            return res.data().event().match().teams();
        } catch (Exception e) {
            log.warn("매치 팀 정보 조회 실패 (matchId={}): {}", matchId, e.toString());
            return List.of();
        }
    }

    /** 매치의 게임(세트) 목록. 진행중 매치는 캐시하지 않고 매번 조회한다. */
    public List<EventDetailsResponse.Game> getGames(String matchId) {
        List<EventDetailsResponse.Game> cached = completedMatchGames.get(matchId);
        if (cached != null) {
            return cached;
        }
        try {
            EventDetailsResponse res = api.getEventDetails(matchId);
            if (res == null || res.data() == null || res.data().event() == null
                    || res.data().event().match() == null
                    || res.data().event().match().games() == null) {
                return List.of();
            }
            List<EventDetailsResponse.Game> games = res.data().event().match().games();
            boolean allCompleted = !games.isEmpty()
                    && games.stream().allMatch(g -> "completed".equalsIgnoreCase(g.state()));
            if (allCompleted) {
                completedMatchGames.put(matchId, games);
            }
            return games;
        } catch (Exception e) {
            log.warn("getEventDetails 온디맨드 실패 (matchId={}): {}", matchId, e.toString());
            return List.of();
        }
    }

    /**
     * 캐시에 없는 게임(과거 경기 등)의 window/details 를 1회 로드해서 캐시에 넣는다.
     * 라이브 활성 게임은 폴링 스케줄러가 이미 캐시를 채우므로 이 경로를 타지 않는다.
     */
    public void ensureGameLoaded(String gameId) {
        // 열람 순서를 기록해 상한을 넘으면 오래된 것부터 밀어낸다.
        // 진행 중인 라이브 게임은 밀려나면 안 되므로 보호 목록으로 넘긴다.
        cache.touchOnDemand(gameId, java.util.Set.copyOf(cache.getActiveGameIds()));

        if (cache.hasWindow(gameId) && cache.hasDetails(gameId)) {
            return;
        }
        Long until = failedUntil.get(gameId);
        if (until != null && until > System.currentTimeMillis()) {
            return;
        }

        boolean anyLoaded = false;
        try {
            // startingTime=now-lag: 종료된 게임은 마지막 프레임으로 클램핑됨 (2026-08-07 실검증)
            if (!cache.hasWindow(gameId)) {
                WindowResponse window = liveStats.getWindow(gameId);
                if (window != null && window.frames() != null && !window.frames().isEmpty()) {
                    cache.addWindowFrames(gameId, window.gameMetadata(), window.frames());
                    // 경과 시간 계산 기준점 (과거 경기도 동일하게 필요)
                    if (cache.getGameStart(gameId) == null) {
                        String start = liveStats.getGameStartTimestamp(gameId);
                        if (start != null) {
                            try {
                                cache.setGameStart(gameId, java.time.Instant.parse(start));
                            } catch (Exception ignored) {
                                // 시작 시각을 못 구해도 나머지 데이터는 정상 제공
                            }
                        }
                    }
                    anyLoaded = true;
                }
            } else {
                anyLoaded = true;
            }

            if (!cache.hasDetails(gameId)) {
                DetailsResponse details = liveStats.getDetails(gameId);
                if (details != null && details.frames() != null && !details.frames().isEmpty()) {
                    cache.addDetailsFrames(gameId, details.frames());
                    anyLoaded = true;
                }
            }
        } catch (Exception e) {
            log.warn("과거 게임 온디맨드 로드 실패 (gameId={}): {}", gameId, e.toString());
        }

        if (anyLoaded) {
            failedUntil.remove(gameId);
            loadTimeline(gameId);
        } else {
            // 데이터가 아예 없는 게임(피드 미보존, 잘못된 id 등) — 잠시 재시도 금지
            failedUntil.put(gameId, System.currentTimeMillis() + FAIL_CACHE_MS);
        }
    }
}
