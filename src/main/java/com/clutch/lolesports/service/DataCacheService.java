package com.clutch.lolesports.service;

import com.clutch.lolesports.dto.external.DetailsResponse;
import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.dto.external.ScheduleResponse;
import com.clutch.lolesports.dto.external.StandingsResponse;
import com.clutch.lolesports.dto.external.WindowResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 폴링 결과 인메모리 캐시.
 * 서버가 소스를 한 번만 폴링하고, 사용자 요청은 전부 이 캐시에서 응답한다 (소스 직접 호출 금지).
 *
 * 인게임 프레임은 "최신 1개"가 아니라 타임라인 버퍼로 보관한다.
 * 피드는 약 10초 블록으로 초 단위 프레임 수십 개를 주는데, 전부 쌓아두고
 * API 계층이 "now - display-lag" 시점의 프레임을 골라 응답하면
 * 프론트가 2.5초마다 조회할 때마다 실제로 전진한 프레임을 받는다 (부드러운 갱신).
 */
@Service
public class DataCacheService {

    /**
     * 프레임 버퍼 보관 범위 (최신 프레임 기준 과거 N초).
     * 골드차 추이 그래프가 게임 전체 구간을 필요로 하므로 한 게임 길이(최대 ~90분)보다 넉넉히 잡는다.
     */
    private static final long BUFFER_RETENTION_SECONDS = 7200;

    /**
     * 진행 중인 매치 하나에 대한 getLive와 getEventDetails 조합 상태다.
     *
     * @param matchId 외부 매치 ID
     * @param blockName 일정 블록 이름
     * @param leagueName 리그 이름
     * @param startTime 매치 시작 시각 문자열
     * @param teams 참가 팀과 현재 세트 승수
     * @param games 매치의 세트 목록
     * @param bestOf 매치 최대 세트 수 또는 알 수 없으면 null
     * @param activeGameId 진행 중인 게임 ID 또는 없으면 null
     */
    public record LiveMatch(
            String matchId,
            String blockName,
            String leagueName,
            String startTime,
            List<ScheduleResponse.Team> teams,
            List<EventDetailsResponse.Game> games,
            Integer bestOf,
            String activeGameId
    ) {
    }

    /** 게임 하나의 window 프레임 타임라인 (rfc460Timestamp 키 → 중복 자동 제거) */
    private static class WindowBuffer {
        volatile WindowResponse.GameMetadata meta;
        /**
         * 게임 시작 시각(첫 프레임). 피드에 게임 경과 시간 필드가 없어서
         * 이 값을 기준으로 계산한다. 버퍼는 오래된 프레임을 지우므로 별도 보관.
         */
        volatile Instant gameStart;
        final ConcurrentSkipListMap<Instant, WindowResponse.Frame> frames = new ConcurrentSkipListMap<>();
    }

    private static class DetailsBuffer {
        final ConcurrentSkipListMap<Instant, DetailsResponse.Frame> frames = new ConcurrentSkipListMap<>();
    }

    private final AtomicReference<ScheduleResponse> schedule = new AtomicReference<>();
    private final AtomicReference<StandingsResponse> standings = new AtomicReference<>();
    private final AtomicReference<List<LiveMatch>> liveMatches = new AtomicReference<>(List.of());
    private final AtomicReference<List<LiveMatch>> bettingMatches = new AtomicReference<>(List.of());

    private final Map<String, WindowBuffer> windows = new ConcurrentHashMap<>();
    private final Map<String, DetailsBuffer> details = new ConcurrentHashMap<>();
    private final Map<String, Instant> feedFinishedAt = new ConcurrentHashMap<>();

    // ---- 일정/순위 ----

    public void putSchedule(ScheduleResponse r) {
        schedule.set(r);
    }

    public ScheduleResponse getSchedule() {
        return schedule.get();
    }

    public void putStandings(StandingsResponse r) {
        standings.set(r);
    }

    public StandingsResponse getStandings() {
        return standings.get();
    }

    // ---- 라이브 매치 목록 ----

    public void putLiveMatches(List<LiveMatch> matches) {
        liveMatches.set(matches);
    }

    public List<LiveMatch> getLiveMatches() {
        return liveMatches.get();
    }

    /**
     * 라이브 경기와 가까운 예정 경기를 합친 배팅 전용 매치 캐시를 교체한다.
     *
     * @param matches 배팅 이벤트 동기화에 사용할 매치 목록
     */
    public void putBettingMatches(List<LiveMatch> matches) {
        bettingMatches.set(List.copyOf(matches));
    }

    /**
     * 사용자 요청에서 외부 API를 호출하지 않도록 준비된 배팅 전용 매치를 반환한다.
     *
     * @return 배팅 후보 매치 목록
     */
    public List<LiveMatch> getBettingMatches() {
        return bettingMatches.get();
    }

    /** 현재 인게임 폴링 대상 gameId 목록 */
    public List<String> getActiveGameIds() {
        return liveMatches.get().stream()
                .map(LiveMatch::activeGameId)
                .filter(id -> id != null && !id.isBlank())
                .toList();
    }

    // ---- window 프레임 버퍼 ----

    /**
     * 응답의 모든 프레임을 버퍼에 추가한다. rfc460Timestamp 가 같은 프레임은 무시(중복 방지).
     *
     * @return 새로 반영된 프레임들 (시간순) — 펜타킬 감지 등 후속 처리용
     */
    public List<WindowResponse.Frame> addWindowFrames(String gameId,
                                                      WindowResponse.GameMetadata meta,
                                                      List<WindowResponse.Frame> newFrames) {
        WindowBuffer buf = windows.computeIfAbsent(gameId, k -> new WindowBuffer());
        if (meta != null) {
            buf.meta = meta;
        }
        List<WindowResponse.Frame> added = new ArrayList<>();
        for (WindowResponse.Frame f : newFrames) {
            Instant ts = parseTs(f.rfc460Timestamp());
            if (ts != null && buf.frames.putIfAbsent(ts, f) == null) {
                added.add(f);
                if ("finished".equalsIgnoreCase(f.gameState())) {
                    feedFinishedAt.putIfAbsent(gameId, ts);
                }
            }
        }
        pruneOld(buf.frames);
        return added;
    }

    public WindowResponse.GameMetadata getWindowMeta(String gameId) {
        WindowBuffer buf = windows.get(gameId);
        return buf != null ? buf.meta : null;
    }

    /** 게임 시작 시각 (경과 시간 계산용). 아직 확정 못 했으면 null */
    public Instant getGameStart(String gameId) {
        WindowBuffer buf = windows.get(gameId);
        return buf != null ? buf.gameStart : null;
    }

    /** 게임 시작 시각 등록 (첫 프레임 조회로 확정된 값) */
    public void setGameStart(String gameId, Instant start) {
        WindowBuffer buf = windows.computeIfAbsent(gameId, k -> new WindowBuffer());
        buf.gameStart = start;
    }

    /**
     * target 시점 기준 가장 가까운 과거 프레임.
     * target 이전 프레임이 없으면(버퍼 워밍업 직후 등) 가장 오래된 프레임, 버퍼가 비면 null.
     */
    public WindowResponse.Frame getWindowFrameAt(String gameId, Instant target) {
        WindowBuffer buf = windows.get(gameId);
        if (buf == null || buf.frames.isEmpty()) {
            return null;
        }
        Map.Entry<Instant, WindowResponse.Frame> e = buf.frames.floorEntry(target);
        return e != null ? e.getValue() : buf.frames.firstEntry().getValue();
    }

    /**
     * target 시점까지의 프레임을 시간순으로 반환 (골드차 추이 그래프용).
     * 프레임이 초당 수 개라 그대로 주면 너무 많으므로 stepSeconds 간격으로 솎아낸다.
     */
    public List<WindowResponse.Frame> getWindowSeries(String gameId, Instant until, int stepSeconds) {
        WindowBuffer buf = windows.get(gameId);
        if (buf == null || buf.frames.isEmpty()) {
            return List.of();
        }
        List<WindowResponse.Frame> out = new ArrayList<>();
        Instant next = null;
        for (Map.Entry<Instant, WindowResponse.Frame> e : buf.frames.headMap(until, true).entrySet()) {
            if (next == null || !e.getKey().isBefore(next)) {
                out.add(e.getValue());
                next = e.getKey().plusSeconds(stepSeconds);
            }
        }
        return out;
    }

    /** 버퍼의 가장 새 프레임 (최신 우선 모드용 — 피드가 주는 물리적 최신, 20~30초 지연) */
    public WindowResponse.Frame getNewestWindowFrame(String gameId) {
        WindowBuffer buf = windows.get(gameId);
        return (buf == null || buf.frames.isEmpty()) ? null : buf.frames.lastEntry().getValue();
    }

    public boolean hasWindow(String gameId) {
        WindowBuffer buf = windows.get(gameId);
        return buf != null && !buf.frames.isEmpty();
    }

    /**
     * 피드 기준으로 이 세트가 끝났는지.
     *
     * esports-api 의 state=completed 는 실제 종료보다 약 5분 늦다 (2026-08-13 실측).
     * 피드는 종료 즉시 마지막 프레임을 gameState=finished 로 주므로, 화면의 "세트 종료"
     * 표시는 이 값을 쓴다. 세트 승패는 여기서 알 수 없다 — 그건 gameWins 로 판정한다.
     */
    public boolean isFeedFinished(String gameId) {
        return feedFinishedAt.containsKey(gameId);
    }

    /**
     * livestats 피드가 처음 finished 상태를 알린 시각을 반환한다.
     *
     * @param gameId 외부 게임 ID
     * @return 최초 종료 프레임 시각 또는 아직 종료되지 않았으면 null
     */
    public Instant getFeedFinishedAt(String gameId) {
        return feedFinishedAt.get(gameId);
    }

    // ---- details 프레임 버퍼 ----

    public void addDetailsFrames(String gameId, List<DetailsResponse.Frame> newFrames) {
        DetailsBuffer buf = details.computeIfAbsent(gameId, k -> new DetailsBuffer());
        for (DetailsResponse.Frame f : newFrames) {
            Instant ts = parseTs(f.rfc460Timestamp());
            if (ts != null) {
                buf.frames.putIfAbsent(ts, f);
            }
        }
        pruneOld(buf.frames);
    }

    public DetailsResponse.Frame getDetailsFrameAt(String gameId, Instant target) {
        DetailsBuffer buf = details.get(gameId);
        if (buf == null || buf.frames.isEmpty()) {
            return null;
        }
        Map.Entry<Instant, DetailsResponse.Frame> e = buf.frames.floorEntry(target);
        return e != null ? e.getValue() : buf.frames.firstEntry().getValue();
    }

    public DetailsResponse.Frame getNewestDetailsFrame(String gameId) {
        DetailsBuffer buf = details.get(gameId);
        return (buf == null || buf.frames.isEmpty()) ? null : buf.frames.lastEntry().getValue();
    }

    public boolean hasDetails(String gameId) {
        DetailsBuffer buf = details.get(gameId);
        return buf != null && !buf.frames.isEmpty();
    }

    // ---- 버퍼 해제 ----

    /**
     * 게임 하나의 프레임 버퍼를 통째로 비운다.
     *
     * pruneOld 는 "한 게임 안에서" 오래된 프레임만 자르고 그마저 새 프레임이 들어올 때만 돈다.
     * 경기가 끝나면 폴링이 멈춰 그 정리가 영영 실행되지 않으므로, 종료 시 명시적으로 호출해야 한다.
     * DB 적재가 성공한 뒤에 부를 것 — 캐시가 사라지면 적재도 트리거 판정도 할 수 없다.
     */
    public void evictGame(String gameId) {
        windows.remove(gameId);
        details.remove(gameId);
        lastAccess.remove(gameId);
    }

    /**
     * 열람용으로 올라온 게임 버퍼의 상한.
     *
     * 라이브 게임은 종료 시 evictGame 으로 정리되지만, 사용자가 과거 경기를 열면
     * ensureGameLoaded 가 캐시를 채우고 아무도 지우지 않는다. 여러 경기를 열어볼수록
     * 계속 쌓이므로(게임당 60~80MB) 오래 안 쓴 것부터 밀어낸다.
     */
    private static final int MAX_ON_DEMAND_GAMES = 4;

    /** gameId → 마지막 접근 시각(ms). 접근 순서만 알면 되므로 프레임과 분리해 둔다 */
    private final Map<String, Long> lastAccess = new ConcurrentHashMap<>();

    /**
     * 열람으로 올라온 게임을 등록하고, 상한을 넘으면 가장 오래 안 쓴 게임을 해제한다.
     * 라이브 폴링 경로는 이걸 호출하지 않는다 — 진행 중인 경기가 밀려나면 안 되기 때문.
     */
    public void touchOnDemand(String gameId, java.util.Set<String> protectedIds) {
        lastAccess.put(gameId, System.currentTimeMillis());

        if (lastAccess.size() <= MAX_ON_DEMAND_GAMES) {
            return;
        }
        lastAccess.entrySet().stream()
                .filter(e -> !protectedIds.contains(e.getKey()))
                .sorted(Map.Entry.comparingByValue())
                .limit(Math.max(0, lastAccess.size() - MAX_ON_DEMAND_GAMES))
                .map(Map.Entry::getKey)
                .toList()
                .forEach(this::evictGame);
    }

    /** 현재 버퍼를 들고 있는 게임 수 (모니터링용) */
    public int bufferedGameCount() {
        return windows.size();
    }

    /** 버퍼가 있는 게임 id 목록 */
    public java.util.Set<String> bufferedGameIds() {
        return java.util.Set.copyOf(windows.keySet());
    }

    // ---- 내부 유틸 ----

    private static <V> void pruneOld(ConcurrentSkipListMap<Instant, V> frames) {
        if (frames.isEmpty()) {
            return;
        }
        Instant cutoff = frames.lastKey().minusSeconds(BUFFER_RETENTION_SECONDS);
        frames.headMap(cutoff).clear();
    }

    private static Instant parseTs(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- 디버그 ----

    /** /api/debug 노출용 내부 상태 스냅샷 */
    public Map<String, Object> debugSnapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scheduleCached", schedule.get() != null);
        out.put("standingsCached", standings.get() != null);
        out.put("liveMatches", liveMatches.get());
        out.put("bettingMatches", bettingMatches.get());
        out.put("activeGameIds", getActiveGameIds());

        Map<String, Object> windowInfo = new LinkedHashMap<>();
        windows.forEach((id, buf) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("frameCount", buf.frames.size());
            entry.put("oldestFrame", buf.frames.isEmpty() ? null : buf.frames.firstKey().toString());
            entry.put("newestFrame", buf.frames.isEmpty() ? null : buf.frames.lastKey().toString());
            windowInfo.put(id, entry);
        });
        out.put("windowBuffers", windowInfo);

        Map<String, Object> detailsInfo = new LinkedHashMap<>();
        details.forEach((id, buf) -> detailsInfo.put(id, Map.of(
                "frameCount", buf.frames.size(),
                "newestFrame", buf.frames.isEmpty() ? "-" : buf.frames.lastKey().toString()
        )));
        out.put("detailsBuffers", detailsInfo);
        return out;
    }
}
