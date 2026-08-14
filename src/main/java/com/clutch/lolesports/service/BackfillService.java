package com.clutch.lolesports.service;

import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.dto.external.ScheduleResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 과거 경기를 소스에서 받아 DB 로 적재한다 (일회성 백필).
 *
 * 라이브 경로와 달리 "종료 감지"를 기다릴 수 없으므로, 일정 캐시에서 완료된 매치를 골라
 * 세트별로 타임라인을 수집한 뒤 곧바로 적재한다.
 *
 * 세트 하나에 소스 요청이 수백 건 필요하므로(약 1.5분) 순차로 처리하고,
 * 적재가 끝난 세트는 즉시 캐시에서 해제해 메모리가 누적되지 않게 한다.
 */
@Service
public class BackfillService {

    private static final Logger log = LoggerFactory.getLogger(BackfillService.class);

    /** 동시에 여러 백필이 돌면 소스에 부담이 크고 중복 적재가 생긴다 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 전체 백필은 두 시간 넘게 걸려 HTTP 응답을 붙잡고 있을 수 없다 — 백그라운드로 돌린다 */
    private final java.util.concurrent.ExecutorService worker =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "backfill");
                t.setDaemon(true);
                return t;
            });

    /** 진행 상황 — /api/admin/backfill/status 로 조회한다 */
    private final java.util.concurrent.atomic.AtomicInteger totalMatches =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger scanned =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger persistedCount =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger skippedCount =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger failedCount =
            new java.util.concurrent.atomic.AtomicInteger();
    private volatile long startedAtMs;
    private volatile String currentMatch;

    private final DataCacheService cache;
    private final HistoricalGameService historical;
    private final GamePersistService persistService;

    public BackfillService(DataCacheService cache,
                           HistoricalGameService historical,
                           GamePersistService persistService) {
        this.cache = cache;
        this.historical = historical;
        this.persistService = persistService;
    }

    public record Result(int matchesScanned, int gamesPersisted, int gamesSkipped, int gamesFailed) {
    }

    /**
     * 백그라운드로 백필을 시작하고 즉시 반환한다.
     *
     * @return 시작했으면 true, 이미 실행 중이면 false
     */
    public boolean startAsync(int limit, boolean includeAll) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        resetProgress();
        worker.submit(() -> {
            try {
                runBackfill(limit, includeAll);
            } catch (Exception e) {
                log.error("백필 중단: {}", e.toString(), e);
            } finally {
                running.set(false);
                currentMatch = null;
            }
        });
        return true;
    }

    private void resetProgress() {
        startedAtMs = System.currentTimeMillis();
        totalMatches.set(0);
        scanned.set(0);
        persistedCount.set(0);
        skippedCount.set(0);
        failedCount.set(0);
        currentMatch = null;
    }

    /** 진행 상황 스냅샷 */
    public java.util.Map<String, Object> progress() {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        int done = scanned.get();
        int total = totalMatches.get();
        long elapsed = startedAtMs == 0 ? 0 : (System.currentTimeMillis() - startedAtMs) / 1000;

        out.put("running", running.get());
        out.put("matchesTotal", total);
        out.put("matchesScanned", done);
        out.put("gamesPersisted", persistedCount.get());
        out.put("gamesSkipped", skippedCount.get());
        out.put("gamesFailed", failedCount.get());
        out.put("currentMatch", currentMatch);
        out.put("elapsedSeconds", elapsed);
        if (running.get() && done > 0 && total > 0) {
            out.put("progressPercent", Math.round(done * 1000.0 / total) / 10.0);
            out.put("etaSeconds", (long) ((double) elapsed / done * (total - done)));
        }
        out.put("bufferedGames", cache.bufferedGameCount());
        return out;
    }

    public boolean isRunning() {
        return running.get();
    }

    private Result runBackfill(int limit, boolean includeAll) {
        List<ScheduleResponse.Event> completed = completedMatches();
        int target = Math.min(limit, completed.size());
        totalMatches.set(target);
        log.info("백필 시작 — 완료된 매치 {}건 중 {}건 처리", completed.size(), target);

        for (ScheduleResponse.Event event : completed) {
            if (scanned.get() >= limit) {
                break;
            }
            String matchId = event.match().id();
            currentMatch = matchId;
            int done = scanned.incrementAndGet();

            List<EventDetailsResponse.Game> games = historical.getGames(matchId);
            if (games.isEmpty()) {
                log.warn("백필 — 매치 {} 의 세트 목록을 가져오지 못했다", matchId);
                continue;
            }

            for (EventDetailsResponse.Game game : games) {
                if (game.id() == null || !"completed".equalsIgnoreCase(game.state())) {
                    continue;
                }
                if (!includeAll && persistService.isAlreadyPersisted(game.id())) {
                    skippedCount.incrementAndGet();
                    continue;
                }

                if (persistOne(event, game)) {
                    persistedCount.incrementAndGet();
                } else {
                    failedCount.incrementAndGet();
                }
            }

            if (done % 10 == 0 || done == target) {
                log.info("백필 진행 {}/{} — 적재 {} · 건너뜀 {} · 실패 {} · 버퍼 {}",
                        done, target, persistedCount.get(), skippedCount.get(),
                        failedCount.get(), cache.bufferedGameCount());
            }
        }

        Result result = new Result(scanned.get(), persistedCount.get(),
                skippedCount.get(), failedCount.get());
        log.info("백필 완료 — {}", result);
        return result;
    }

    /** 세트 하나: 소스에서 타임라인 수집 → 적재 → 캐시 해제 */
    private boolean persistOne(ScheduleResponse.Event event, EventDetailsResponse.Game game) {
        String gameId = game.id();
        long began = System.currentTimeMillis();
        try {
            // 소스에서 전 구간 프레임을 받아 캐시에 채운다 (골드 추이 그래프용)
            historical.ensureGameLoaded(gameId);

            if (!cache.hasWindow(gameId)) {
                log.warn("백필 — 게임 {} 통계 미제공(스탯 비활성 경기로 보임)", gameId);
                return false;
            }

            // 이 세트의 진영 (세트마다 바뀐다)
            String blueTeamId = null;
            String redTeamId = null;
            if (game.teams() != null) {
                for (EventDetailsResponse.GameTeam t : game.teams()) {
                    if ("blue".equalsIgnoreCase(t.side())) {
                        blueTeamId = t.id();
                    } else if ("red".equalsIgnoreCase(t.side())) {
                        redTeamId = t.id();
                    }
                }
            }

            // 백필 대상은 이미 끝난 매치라 completed 가 맞다
            GamePersistService.MatchContext ctx = new GamePersistService.MatchContext(
                    event.match().id(),
                    event.league() != null ? event.league().name() : null,
                    event.blockName(),
                    event.startTime(),
                    "completed",
                    event.match().strategy() != null ? event.match().strategy().count() : null,
                    game.number() != null ? game.number() : 1,
                    event.match().teams() != null ? event.match().teams() : List.of(),
                    blueTeamId,
                    redTeamId);

            boolean ok = persistService.persistGame(gameId, ctx);
            if (ok) {
                log.info("백필 적재 성공 gameId={} ({}초)", gameId, (System.currentTimeMillis() - began) / 1000);
            }
            return ok;

        } catch (Exception e) {
            log.warn("백필 적재 실패 gameId={}: {}", gameId, e.toString());
            return false;
        } finally {
            // 적재 성공 여부와 무관하게 해제한다 — 실패분은 소스에서 다시 받으면 된다.
            // 남겨두면 백필이 도는 동안 메모리가 계속 불어난다.
            cache.evictGame(gameId);
        }
    }

    /** 일정 캐시에서 완료된 매치를 최신순으로 (팀이 확정된 것만) */
    private List<ScheduleResponse.Event> completedMatches() {
        ScheduleResponse schedule = cache.getSchedule();
        if (schedule == null || schedule.data() == null || schedule.data().schedule() == null
                || schedule.data().schedule().events() == null) {
            return List.of();
        }
        List<ScheduleResponse.Event> out = new ArrayList<>(schedule.data().schedule().events().stream()
                .filter(e -> "match".equalsIgnoreCase(e.type()))
                .filter(e -> e.match() != null && e.match().id() != null)
                .filter(e -> "completed".equalsIgnoreCase(e.state()))
                .filter(e -> e.match().teams() != null && e.match().teams().size() == 2)
                .toList());

        // 최신 경기부터 — 사용자가 먼저 열어볼 가능성이 높다
        out.sort((a, b) -> {
            Instant ta = parse(a.startTime());
            Instant tb = parse(b.startTime());
            if (ta == null || tb == null) {
                return 0;
            }
            return tb.compareTo(ta);
        });
        return out;
    }

    private static Instant parse(String s) {
        try {
            return s == null ? null : Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
