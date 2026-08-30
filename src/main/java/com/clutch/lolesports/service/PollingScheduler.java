package com.clutch.lolesports.service;

import com.clutch.lolesports.client.LiveStatsClient;
import com.clutch.lolesports.client.LolesportsApiClient;
import com.clutch.lolesports.config.LolesportsProperties;
import com.clutch.lolesports.dto.external.DetailsResponse;
import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.dto.external.ScheduleResponse;
import com.clutch.lolesports.dto.external.StandingsResponse;
import com.clutch.lolesports.dto.external.WindowResponse;
import com.clutch.lolesports.source.ExternalSourceMode;
import com.clutch.lolesports.source.ExternalSourceProperties;
import com.clutch.lolesports.source.ExternalSourceState;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 폴링 스케줄러.
 *  - 라이브 감시: REAL은 getLive 60초, STUB replay는 0.25초 간격 → 진행중 매치의 gameId 를 활성 게임으로 등록
 *  - 인게임: 활성 게임이 있을 때만 window/details 1초 간격
 *  - 메타(일정/순위): 앱 시작 직후 1회 + 5분 간격
 *
 * 각 작업은 독립적인 백오프를 가진다. 연속 실패 시 지수적으로 간격을 늘리고,
 * 예외는 전부 잡아서 스케줄러 스레드가 죽지 않게 한다.
 */
@Component
public class PollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(PollingScheduler.class);
    private static final Duration BETTING_PREWARM_BEFORE_START = Duration.ofMinutes(30);
    private static final Duration BETTING_PREWARM_AFTER_START = Duration.ofMinutes(5);

    private final LolesportsApiClient api;
    private final LiveStatsClient liveStats;
    private final DataCacheService cache;
    private final PentakillDetector pentakillDetector;
    private final FirstBloodDetector firstBloodDetector;
    private final GamePersistService persistService;
    private final SetWinnerTracker setWinners;
    private final HistoricalGameService historical;
    private final LolesportsProperties props;
    private final ExternalSourceState sourceState;

    /** matchId → 종료로 판정한 시각(epoch ms). 유지 시간이 지나면 라이브에서 뺀다 */
    private final Map<String, Long> finishedAt = new ConcurrentHashMap<>();

    private final Backoff liveBackoff;
    private final Backoff inGameBackoff;
    private final Backoff metaBackoff;

    /** 직전 폴링에서 활성이었던 게임 — 종료 감지 후 정리용 */
    private final Set<String> previousActiveGames = new HashSet<>();
    /**
     * gameId → 그 게임이 속한 매치 스냅샷.
     *
     * 종료된 게임은 getLive 응답에서 빠지므로, 적재 시점에는 매치 정보를 얻을 수 없다.
     * 활성일 때 미리 담아두고 적재가 끝나면 지운다.
     */
    private final Map<String, DataCacheService.LiveMatch> lastKnownMatches = new ConcurrentHashMap<>();
    /**
     * 통계 404 를 받은 게임 → 재시도 대기 상태 (시각 + 연속 실패 횟수).
     *
     * 404 는 영구 상태가 아니다. 경기 시작 전에는 "Stats are disabled" 가 오다가
     * 실제 인게임이 시작되면 정상 응답으로 바뀐다 (2026-08-08 DCG vs MVK 확인).
     *
     * 예전에는 여기에 지수 백오프를 걸었으나, 그 대기 때문에 소스가 통계를 연 뒤에도
     * 최대 60초를 더 기다려 화면이 늦게 떴다 (2026-08-19 GEN vs KT: 약 6분).
     * 부하의 원인은 재시도 간격이 아니라 "영영 안 열리는 리그"였으므로, 그쪽은
     * statsDisabledLeagues 로 아예 폴링하지 않고 나머지는 1초마다 그대로 물어본다.
     */
    private final Map<String, Integer> statsFailures = new ConcurrentHashMap<>();

    /**
     * 화면에 "데이터 미제공" 을 띄우기까지 필요한 연속 실패 횟수.
     * 한 번의 404 로 덮으면 시작 직전에 잠깐 뜨는 404 까지 미제공으로 보인다.
     */
    private static final int STATS_UNAVAILABLE_AFTER_FAILURES = 3;

    @Autowired
    public PollingScheduler(LolesportsApiClient api,
                            LiveStatsClient liveStats,
                            DataCacheService cache,
                            PentakillDetector pentakillDetector,
                            FirstBloodDetector firstBloodDetector,
                            GamePersistService persistService,
                            SetWinnerTracker setWinners,
                            HistoricalGameService historical,
                            LolesportsProperties props,
                            ExternalSourceState sourceState) {
        this.api = api;
        this.liveStats = liveStats;
        this.cache = cache;
        this.pentakillDetector = pentakillDetector;
        this.firstBloodDetector = firstBloodDetector;
        this.persistService = persistService;
        this.setWinners = setWinners;
        this.historical = historical;
        this.props = props;
        this.sourceState = sourceState;
        long base = props.poll().backoffBaseMs();
        long max = props.poll().backoffMaxMs();
        this.liveBackoff = new Backoff(base, max);
        this.inGameBackoff = new Backoff(base, max);
        this.metaBackoff = new Backoff(base, max);
    }

    /** 첫 킬 감지기 없이 조립하는 기존 단위 테스트용 생성자. */
    PollingScheduler(LolesportsApiClient api,
                     LiveStatsClient liveStats,
                     DataCacheService cache,
                     PentakillDetector pentakillDetector,
                     GamePersistService persistService,
                     SetWinnerTracker setWinners,
                     HistoricalGameService historical,
                     LolesportsProperties props,
                     ExternalSourceState sourceState) {
        this(api, liveStats, cache, pentakillDetector, null, persistService,
                setWinners, historical, props, sourceState);
    }

    /** 기존 단위 테스트와 패키지 내부 조립을 위한 기본 REAL 상태 생성자. */
    PollingScheduler(LolesportsApiClient api,
                     LiveStatsClient liveStats,
                     DataCacheService cache,
                     PentakillDetector pentakillDetector,
                     GamePersistService persistService,
                     SetWinnerTracker setWinners,
                     HistoricalGameService historical,
                     LolesportsProperties props) {
        this(api, liveStats, cache, pentakillDetector, null, persistService, setWinners, historical, props,
                new ExternalSourceState(new ExternalSourceProperties(
                        false, ExternalSourceMode.REAL, null, null)));
    }

    /** 기존 단위 테스트와 패키지 내부 조립을 위한 기본 REAL 상태 생성자. */
    PollingScheduler(LolesportsApiClient api,
                     LiveStatsClient liveStats,
                     DataCacheService cache,
                     PentakillDetector pentakillDetector,
                     GamePersistService persistService,
                     SetWinnerTracker setWinners,
                     LolesportsProperties props) {
        this(api, liveStats, cache, pentakillDetector, null, persistService, setWinners, null, props,
                new ExternalSourceState(new ExternalSourceProperties(
                        false, ExternalSourceMode.REAL, null, null)));
    }

    // ---- 1) 라이브 경기 감시 (60초) ----

    public void pollLiveMatches() {
        sourceState.withReadLock(this::pollLiveMatchesInternal);
    }

    /** 실제 소스는 호출 부하를 고려해 기존 주기로 감시한다. */
    @Scheduled(fixedDelayString = "${lolesports.poll.live-check-ms:60000}")
    public void pollRealLiveMatches() {
        if (sourceState.mode() != ExternalSourceMode.STUB) {
            pollLiveMatches();
        }
    }

    /**
     * replay는 시간축을 최대 60배까지 압축한다. 상태 전환은 0.25초마다 읽어,
     * 60배속에서도 최대 재생 시간 15초 안에 반영한다.
     */
    @Scheduled(fixedDelay = 250)
    public void pollStubLiveMatches() {
        if (sourceState.mode() == ExternalSourceMode.STUB) {
            pollLiveMatches();
        }
    }

    private void pollLiveMatchesInternal() {
        if (!liveBackoff.allowed()) {
            return;
        }
        try {
            ScheduleResponse live = api.getLive();
            List<ResolvedLiveMatch> resolvedMatches = new ArrayList<>();

            if (live != null && live.data() != null && live.data().schedule() != null
                    && live.data().schedule().events() != null) {
                for (ScheduleResponse.Event event : live.data().schedule().events()) {
                    // type=show(방송 대기 화면 등)는 match 가 없음
                    if (event.match() == null || event.match().id() == null) {
                        continue;
                    }
                    resolvedMatches.add(resolveLiveMatch(event));
                }
            }

            persistLiveMatches(resolvedMatches);
            List<DataCacheService.LiveMatch> matches = resolvedMatches.stream()
                    .map(ResolvedLiveMatch::liveMatch)
                    .toList();
            matches = dropLongFinished(matches);
            cache.putLiveMatches(matches);
            cache.putBettingMatches(resolveBettingMatches(matches));
            rememberActiveMatches(matches);
            cleanupFinishedGames();
            liveBackoff.success();

            if (!matches.isEmpty()) {
                log.info("라이브 매치 {}건, 활성 게임: {}", matches.size(), cache.getActiveGameIds());
            }
        } catch (Exception e) {
            liveBackoff.failure();
            log.warn("getLive 폴링 실패 (연속 {}회): {}", liveBackoff.failures(), e.toString());
        }
    }

    /** 라이브 매치별 선저장 실패를 격리해 나머지 캐시 갱신을 계속한다. */
    private void persistLiveMatches(List<ResolvedLiveMatch> matches) {
        for (ResolvedLiveMatch resolved : matches) {
            DataCacheService.LiveMatch match = resolved.liveMatch();
            try {
                persistService.persistLiveMatch(match, resolved.origin());
            } catch (RuntimeException exception) {
                log.warn("라이브 매치 선저장 실패 (matchId={}): {}",
                        match.matchId(), exception.toString());
            }
        }
    }

    /**
     * 실제 라이브 매치에 배팅 창이 가까운 예정 매치를 합쳐 배팅 전용 캐시를 만든다.
     *
     * @param liveMatches getLive에서 확인한 실제 라이브 매치 목록
     * @return 매치 ID가 중복되지 않는 배팅 후보 매치 목록
     */
    private List<DataCacheService.LiveMatch> resolveBettingMatches(
            List<DataCacheService.LiveMatch> liveMatches
    ) {
        Map<String, DataCacheService.LiveMatch> candidates = new java.util.LinkedHashMap<>();
        liveMatches.forEach(match -> candidates.put(match.matchId(), match));

        ScheduleResponse schedule = cache.getSchedule();
        if (schedule == null || schedule.data() == null || schedule.data().schedule() == null
                || schedule.data().schedule().events() == null) {
            return List.copyOf(candidates.values());
        }

        Instant now = Instant.now();
        for (ScheduleResponse.Event event : schedule.data().schedule().events()) {
            if (!isBettingPrewarmCandidate(event, now)
                    || candidates.containsKey(event.match().id())) {
                continue;
            }
            DataCacheService.LiveMatch candidate = resolveLiveMatch(event).liveMatch();
            candidates.put(candidate.matchId(), candidate);
        }
        return List.copyOf(candidates.values());
    }

    /**
     * 배팅 시간 계산에 필요한 정보를 미리 적재할 가까운 예정 경기인지 확인한다.
     *
     * @param event 일정 이벤트
     * @param now 현재 UTC Instant
     * @return 현재 기준 30분 전부터 시작 5분 후 범위의 미완료 경기이면 true
     */
    private boolean isBettingPrewarmCandidate(ScheduleResponse.Event event, Instant now) {
        if (event == null || event.match() == null || event.match().id() == null
                || event.startTime() == null
                || "completed".equalsIgnoreCase(event.state())) {
            return false;
        }
        try {
            Instant scheduledStart = Instant.parse(event.startTime());
            return !scheduledStart.isAfter(now.plus(BETTING_PREWARM_BEFORE_START))
                    && now.isBefore(scheduledStart.plus(BETTING_PREWARM_AFTER_START));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 종료된 지 오래된 매치를 라이브 목록에서 뺀다.
     *
     * 소스의 getLive 는 경기가 끝나도 언제 내려갈지 예측할 수 없다 (2026-08-14 OME vs DOG:
     * 3세트 종료·outcome 확정 후 4.8분이 지나도 잔류). 그래서 소스를 기다리지 않고,
     * 우리가 종료로 판정한 시각부터 세어 유지 시간이 지나면 제거한다.
     *
     * 종료 직후 바로 빼지 않는 이유는 사용자가 최종 스코어를 확인할 시간이 필요해서다.
     */
    List<DataCacheService.LiveMatch> dropLongFinished(List<DataCacheService.LiveMatch> matches) {
        long now = System.currentTimeMillis();
        long retainMs = props.liveRetainAfterFinishSeconds() * 1000L;
        List<DataCacheService.LiveMatch> kept = new ArrayList<>(matches.size());

        for (DataCacheService.LiveMatch m : matches) {
            if (!m.isFinished()) {
                finishedAt.remove(m.matchId());   // 아직 진행중 — 타이머를 걸어두지 않는다
                kept.add(m);
                continue;
            }
            // 처음 종료로 판정한 시각을 기준으로 삼는다 (소스의 종료 시각이 아니다)
            long since = finishedAt.computeIfAbsent(m.matchId(), k -> {
                log.info("매치 {} 종료 판정 — {}초 뒤 라이브에서 내린다",
                        k, props.liveRetainAfterFinishSeconds());
                return now;
            });
            if (now - since < retainMs) {
                kept.add(m);
            } else {
                log.info("매치 {} 라이브 목록에서 제거 (종료 후 {}초 경과)",
                        m.matchId(), (now - since) / 1000);
                setWinners.clearMatch(m.matchId());
            }
        }

        // 소스에서 이미 사라진 매치의 타이머는 정리한다
        finishedAt.keySet().removeIf(id ->
                matches.stream().noneMatch(m -> m.matchId().equals(id)));
        return kept;
    }

    /**
     * getEventDetails로 진행 중 게임과 팀을 결합하고 배팅 종료 판단 정보까지 LiveMatch로 조립한다.
     *
     * @param event getLive 또는 일정 캐시에서 조회한 매치 이벤트
     * @return 팀·세트·best-of 정보가 결합된 라이브 매치 캐시 값
     */
    private ResolvedLiveMatch resolveLiveMatch(ScheduleResponse.Event event) {
        String matchId = event.match().id();
        List<EventDetailsResponse.Game> games = List.of();
        List<ScheduleResponse.Team> detailTeams = List.of();
        String activeGameId = null;
        Integer bestOf = null;
        HistoricalGameService.MatchOrigin origin = null;

        try {
            EventDetailsResponse details = api.getEventDetails(matchId);
            if (details != null && details.data() != null && details.data().event() != null
                    && details.data().event().match() != null) {
                EventDetailsResponse.Event detailEvent = details.data().event();
                EventDetailsResponse.Match m = detailEvent.match();
                origin = matchOrigin(detailEvent);
                if (m.teams() != null) {
                    detailTeams = m.teams();
                }
                if (m.strategy() != null) {
                    bestOf = m.strategy().count();
                }
                if (m.games() != null) {
                    games = m.games();
                    activeGameId = games.stream()
                            .filter(g -> "inProgress".equalsIgnoreCase(g.state()))
                            .map(EventDetailsResponse.Game::id)
                            .findFirst()
                            .orElse(null);
                }
            }
        } catch (Exception e) {
            // 매치 하나 실패해도 나머지 라이브 매치 처리는 계속한다
            log.warn("getEventDetails 실패 (matchId={}): {}", matchId, e.toString());
        }

        List<ScheduleResponse.Team> teams = mergeTeamIds(event.match().teams(), detailTeams);

        // gameWins 증가분으로 세트 승자를 확정한다 (세트별 승패를 주는 필드가 없다)
        setWinners.observe(matchId, teams, games);

        // 배팅 이벤트가 Bo3/Bo5의 종료 시점을 판단할 수 있도록 매치 전략도 함께 캐시한다.
        return new ResolvedLiveMatch(
                new DataCacheService.LiveMatch(
                        matchId,
                        event.blockName(),
                        event.league() != null ? event.league().name() : null,
                        event.startTime(),
                        bestOf != null ? bestOf : bestOfFromSchedule(matchId),
                        teams,
                        games,
                        activeGameId
                ),
                origin
        );
    }

    /** 이미 조회한 상세 응답에서 DB 선저장에 필요한 실제 리그·대회 ID를 꺼낸다. */
    private static HistoricalGameService.MatchOrigin matchOrigin(
            EventDetailsResponse.Event event
    ) {
        String leagueId = event.league() != null ? event.league().id() : null;
        String tournamentId = event.tournament() != null ? event.tournament().id() : null;
        if (leagueId == null && tournamentId == null) {
            return null;
        }
        return new HistoricalGameService.MatchOrigin(leagueId, tournamentId);
    }

    /** 라이브 화면 데이터와 같은 상세 응답에서 얻은 DB 적재 출처를 함께 운반한다. */
    private record ResolvedLiveMatch(
            DataCacheService.LiveMatch liveMatch,
            HistoricalGameService.MatchOrigin origin
    ) {
    }

    /**
     * 팀 id 를 채워 넣는다.
     *
     * getLive/getSchedule 의 팀 객체에는 id 가 없고 code·result·record 만 있다.
     * 그래서 진영(블루/레드) 판별과 세트 승자 귀속이 불가능했다 —
     * match_team.external_team_id 가 대부분 비어 있던 원인이다.
     *
     * getEventDetails 는 반대로 팀 id 를 주지만 result(gameWins)·record 를 주지 않는다.
     * 두 응답을 팀 코드로 맞춰 id 만 보완한다.
     *
     * @param base   getLive/getSchedule 팀 (result·record 보유)
     * @param detail getEventDetails 팀 (id 보유)
     */
    private static List<ScheduleResponse.Team> mergeTeamIds(List<ScheduleResponse.Team> base,
                                                            List<ScheduleResponse.Team> detail) {
        if (base == null || base.isEmpty() || detail == null || detail.isEmpty()) {
            return base != null ? base : List.of();
        }
        Map<String, String> idByCode = new HashMap<>();
        for (ScheduleResponse.Team t : detail) {
            if (t.code() != null && t.id() != null) {
                idByCode.put(t.code(), t.id());
            }
        }
        if (idByCode.isEmpty()) {
            return base;
        }

        List<ScheduleResponse.Team> merged = new ArrayList<>(base.size());
        for (ScheduleResponse.Team t : base) {
            String id = t.id() != null ? t.id() : idByCode.get(t.code());
            merged.add(new ScheduleResponse.Team(
                    id, t.name(), t.code(), t.image(), t.result(), t.record()));
        }
        return merged;
    }

    /**
     * 활성 목록에서 빠진 게임 = 종료된 게임. 순서대로 처리한다.
     *
     *   1) DB 적재  — 캐시의 최종값을 저장
     *   2) 캐시 해제 — 적재가 성공했을 때만
     *   3) 감지 상태 정리
     *
     * 적재보다 먼저 캐시를 지우면 저장할 값이 사라진다. 적재가 실패하면 캐시를 남겨
     * 다음 폴링에서 다시 시도하되, 소스에서 재수집도 가능하므로 영구 손실은 아니다.
     */
    private void cleanupFinishedGames() {
        Set<String> current = new HashSet<>(cache.getActiveGameIds());

        for (String gameId : previousActiveGames) {
            if (current.contains(gameId)) {
                continue;
            }
            log.info("게임 종료 감지: {}", gameId);

            boolean persisted = persistFinishedGame(gameId);
            if (persisted) {
                cache.evictGame(gameId);
                pentakillDetector.clearGame(gameId);
                if (firstBloodDetector != null) {
                    firstBloodDetector.clearGame(gameId);
                }
                statsFailures.remove(gameId);
                lastKnownMatches.remove(gameId);
                log.info("게임 {} 적재 후 캐시 해제 (남은 버퍼 {}개)", gameId, cache.bufferedGameCount());
            } else {
                log.warn("게임 {} 적재 실패 — 캐시를 유지하고 다음 기회에 재시도한다", gameId);
            }
        }

        previousActiveGames.clear();
        previousActiveGames.addAll(current);
    }

    /** 활성 게임의 매치 정보를 담아둔다 — 종료 후에는 getLive 에서 사라지기 때문 */
    private void rememberActiveMatches(List<DataCacheService.LiveMatch> matches) {
        for (DataCacheService.LiveMatch m : matches) {
            if (m.activeGameId() != null && !m.activeGameId().isBlank()) {
                lastKnownMatches.put(m.activeGameId(), m);
            }
        }
    }

    /** 종료된 게임을 DB 에 적재. 매치 정보는 직전 라이브 스냅샷에서 가져온다 */
    private boolean persistFinishedGame(String gameId) {
        if (persistService.isAlreadyPersisted(gameId)) {
            return true;   // 이미 저장됨 — 캐시만 비우면 된다
        }
        DataCacheService.LiveMatch owner = lastKnownMatches.get(gameId);
        if (owner == null) {
            log.warn("게임 {} 의 매치 정보를 찾지 못해 적재를 건너뛴다", gameId);
            return false;
        }
        Integer bestOf = owner.bestOf() != null
                ? owner.bestOf()
                : bestOfFromSchedule(owner.matchId());
        // getLive 는 전 리그를 주므로 실제 소속을 확인해 적재한다 (매치당 1회, 이후 캐시)
        return persistService.persistGame(gameId,
                GamePersistService.MatchContext.of(owner, gameId, bestOf,
                        historical.getOrigin(owner.matchId())));
    }

    /** 일정 캐시에서 다전제 수를 찾는다 (라이브 응답에는 없다) */
    private Integer bestOfFromSchedule(String matchId) {
        ScheduleResponse schedule = cache.getSchedule();
        if (schedule == null || schedule.data() == null || schedule.data().schedule() == null
                || schedule.data().schedule().events() == null) {
            return null;
        }
        return schedule.data().schedule().events().stream()
                .filter(e -> e.match() != null && matchId.equals(e.match().id()))
                .map(e -> e.match().strategy() != null ? e.match().strategy().count() : null)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    // ---- 2) 인게임 스탯 (활성 게임 있을 때만, 1초 간격) ----

    @Scheduled(fixedDelayString = "${lolesports.poll.in-game-ms:1000}")
    public void pollInGameStats() {
        sourceState.withReadLock(this::pollInGameStatsInternal);
    }

    private void pollInGameStatsInternal() {
        // livestats 를 아예 제공하지 않는 리그만 제외한다. 나머지는 404 를 받아도
        // 미루지 않고 1초마다 그대로 물어본다 — 소스가 여는 즉시 화면에 뜨게 하기 위함.
        List<String> activeGames = cache.getActiveGameIds().stream()
                .filter(id -> !isStatsDisabledLeague(id))
                .toList();
        if (activeGames.isEmpty()) {
            return; // 활성 게임 없으면 인게임 폴링 중지
        }
        if (!inGameBackoff.allowed()) {
            return;
        }

        boolean anyFailure = false;
        for (String gameId : activeGames) {
            anyFailure |= !pollWindow(gameId);
            anyFailure |= !pollDetails(gameId);
        }

        if (anyFailure) {
            inGameBackoff.failure();
        } else {
            inGameBackoff.success();
        }
    }

    /** @return 성공 여부 (204/404 로 데이터가 아직 없는 경우도 성공으로 취급) */
    private boolean pollWindow(String gameId) {
        try {
            WindowResponse window = liveStats.getWindow(gameId);
            if (window == null || window.frames() == null || window.frames().isEmpty()) {
                return true; // 게임 시작 전 등 — 에러 아님
            }
            // 응답의 모든 프레임(초 단위 수십 개)을 버퍼에 적재. 중복은 버퍼가 걸러줌
            List<WindowResponse.Frame> added =
                    cache.addWindowFrames(gameId, window.gameMetadata(), window.frames());
            clearStatsUnavailable(gameId);
            resolveGameStart(gameId);
            // 감지기는 어느 경기의 세트인지 알아야 그 경기에 걸린 쿠폰 이벤트만 연다
            DataCacheService.LiveMatch owner = lastKnownMatches.get(gameId);
            String externalMatchId = owner != null ? owner.matchId() : null;
            Instant gameStart = cache.getGameStart(gameId);
            for (WindowResponse.Frame frame : added) {
                pentakillDetector.onNewWindowFrame(
                        externalMatchId, gameId, frame, gameStart
                );
                if (firstBloodDetector != null) {
                    firstBloodDetector.onNewWindowFrame(
                            externalMatchId, gameId, frame, gameStart
                    );
                }
            }
            return true;
        } catch (WebClientResponseException.NotFound e) {
            // "Stats are disabled for game ..." — 이 게임은 영구히 데이터가 없다.
            // 폴링 대상에서 제외해야 정상 경기의 폴링까지 백오프에 걸리지 않는다.
            markStatsUnavailable(gameId, e);
            return true;
        } catch (Exception e) {
            log.warn("window 폴링 실패 (gameId={}): {}", gameId, e.toString());
            if (e instanceof WebClientResponseException w) {
                log.warn("window 에러 응답 본문: {}", w.getResponseBodyAsString());
            }
            return false;
        }
    }

    private boolean pollDetails(String gameId) {
        try {
            DetailsResponse res = liveStats.getDetails(gameId);
            if (res == null || res.frames() == null || res.frames().isEmpty()) {
                return true;
            }
            cache.addDetailsFrames(gameId, res.frames());
            clearStatsUnavailable(gameId);
            return true;
        } catch (WebClientResponseException.NotFound e) {
            markStatsUnavailable(gameId, e);
            return true;
        } catch (Exception e) {
            log.warn("details 폴링 실패 (gameId={}): {}", gameId, e.toString());
            return false;
        }
    }

    /**
     * 게임 시작 시각을 한 번만 조회해 캐시에 넣는다 (경과 시간 계산 기준점).
     * 게임당 1회뿐이라 폴링 부하에 영향이 없다.
     */
    private void resolveGameStart(String gameId) {
        if (cache.getGameStart(gameId) != null) {
            return;
        }
        String ts = liveStats.getGameStartTimestamp(gameId);
        if (ts == null) {
            return;
        }
        try {
            cache.setGameStart(gameId, java.time.Instant.parse(ts));
            log.info("게임 {} 시작 시각 확정: {}", gameId, ts);
        } catch (Exception e) {
            log.warn("게임 시작 시각 파싱 실패 (gameId={}, ts={})", gameId, ts);
        }
    }

    /**
     * 이 게임의 리그가 livestats 미제공으로 설정돼 있는지.
     *
     * getLive 는 전 세계 리그를 주는데 일부 리그는 세트 내내 404 만 반환한다.
     * 물어봐야 소용이 없으므로 폴링 대상에서 빼 소스 부하를 줄인다.
     */
    private boolean isStatsDisabledLeague(String gameId) {
        if (props.statsDisabledLeagues().isEmpty()) {
            return false;
        }
        DataCacheService.LiveMatch owner = lastKnownMatches.get(gameId);
        String league = owner != null ? owner.leagueName() : null;
        return league != null && props.statsDisabledLeagues().contains(league);
    }

    /** 통계 404 를 기록한다. 재시도를 미루지 않고 다음 폴링(1초 뒤)에 그대로 다시 묻는다 */
    private void markStatsUnavailable(String gameId, WebClientResponseException e) {
        int failures = statsFailures.merge(gameId, 1, Integer::sum);
        // 첫 실패와 미제공 확정 시점만 남긴다 — 매 재시도마다 찍으면 로그가 넘친다
        if (failures == 1 || failures == STATS_UNAVAILABLE_AFTER_FAILURES) {
            log.info("게임 {} 통계 미제공 {}회 ({})", gameId, failures, e.getResponseBodyAsString());
        }
    }

    /**
     * 통계가 정상 응답으로 돌아왔다 — 대기 상태를 즉시 해제한다.
     *
     * 이게 없으면 404 를 한 번 받은 게임은 그 뒤 데이터가 들어와도 계속
     * statsUnavailable=true 로 남아, 프론트가 폴링을 아예 하지 않는다.
     */
    private void clearStatsUnavailable(String gameId) {
        if (statsFailures.remove(gameId) != null) {
            log.info("게임 {} 통계 복구 — 정상 폴링으로 전환", gameId);
        }
    }

    /**
     * 소스가 이 세트의 통계를 주지 않는 상태인지 (프론트 안내용).
     *
     * getLive 는 전 세계 리그를 다 주는데, 리그에 따라 livestats 가
     * "Stats are disabled for game ..." 404 를 반환한다
     * (2026-08-18 LRN 3V vs LYON 실측). 그런 경기는 화면이 영영 빈 스코어보드가 되므로,
     * 폴링이 이미 파악한 상태를 노출해 프론트가 안내로 대체하고 요청을 멈추게 한다.
     *
     * 판단 기준은 "연속 몇 번 실패했나" 다. 재시도 시각으로 판단하면 대기가 풀리는
     * 순간마다 화면이 깜빡이고, 한 번이라도 실패했나로 판단하면 경기 시작 직전의
     * 일시적인 404 때문에 정상 경기까지 영영 미제공으로 굳는다.
     */
    public boolean isStatsUnavailable(String gameId) {
        if (gameId == null) {
            return false;
        }
        Integer failures = statsFailures.get(gameId);
        return failures != null && failures >= STATS_UNAVAILABLE_AFTER_FAILURES;
    }

    // ---- 3) 일정/순위 (앱 시작시 1회 + 5분) ----

    @Scheduled(fixedDelayString = "${lolesports.poll.meta-ms:300000}")
    public void pollMeta() {
        sourceState.withReadLock(this::pollMetaInternal);
    }

    private void pollMetaInternal() {
        if (!metaBackoff.allowed()) {
            return;
        }
        try {
            ScheduleResponse schedule = fetchScheduleWithHistory();
            if (schedule != null) {
                cache.putSchedule(schedule);
            }
            StandingsResponse standings = api.getStandings();
            if (standings != null) {
                cache.putStandings(standings);
            }
            metaBackoff.success();
            log.info("일정/순위 캐시 갱신 완료");
        } catch (Exception e) {
            metaBackoff.failure();
            log.warn("일정/순위 폴링 실패 (연속 {}회): {}", metaBackoff.failures(), e.toString());
        }
    }

    /** /api/debug 노출용 백오프 상태 */
    public java.util.Map<String, Object> backoffStatus() {
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("liveCheck", liveBackoff.status());
        out.put("inGame", inGameBackoff.status());
        out.put("meta", metaBackoff.status());
        return out;
    }

    /**
     * 외부 소스 전환 직후 이전 폴링 사이클의 메모리 상태를 제거한다.
     * 호출자는 {@link ExternalSourceState}의 쓰기 잠금을 보유해야 한다.
     */
    public void resetForExternalSourceChange() {
        if (cache != null) {
            cache.clearExternalSourceData();
        }
        finishedAt.clear();
        previousActiveGames.clear();
        lastKnownMatches.clear();
        statsFailures.clear();
        liveBackoff.reset();
        inGameBackoff.reset();
        metaBackoff.reset();
        if (pentakillDetector != null) {
            pentakillDetector.clearAll();
        }
        if (firstBloodDetector != null) {
            firstBloodDetector.clearAll();
        }
        if (setWinners != null) {
            setWinners.clearAll();
        }
    }

    /** 과거 일정을 몇 페이지까지 거슬러 받을지 (상대 전적 표본 확보용) */
    private static final int SCHEDULE_HISTORY_PAGES = 3;

    /**
     * 현재 일정 + 과거 페이지를 합쳐 하나의 응답으로 만든다.
     * getSchedule 은 한 번에 80건만 주므로, pages.older 토큰을 따라가며 이어 붙인다.
     * 실패해도 그때까지 모은 분량은 그대로 사용한다.
     */
    private ScheduleResponse fetchScheduleWithHistory() {
        ScheduleResponse first = api.getSchedule();
        if (first == null || first.data() == null || first.data().schedule() == null) {
            return first;
        }

        List<ScheduleResponse.Event> all = new ArrayList<>(
                first.data().schedule().events() != null ? first.data().schedule().events() : List.of());
        String token = first.data().schedule().pages() != null
                ? first.data().schedule().pages().older() : null;

        for (int i = 0; i < SCHEDULE_HISTORY_PAGES && token != null && !token.isBlank(); i++) {
            try {
                ScheduleResponse page = api.getSchedule(token);
                if (page == null || page.data() == null || page.data().schedule() == null
                        || page.data().schedule().events() == null) {
                    break;
                }
                all.addAll(page.data().schedule().events());
                token = page.data().schedule().pages() != null
                        ? page.data().schedule().pages().older() : null;
            } catch (Exception e) {
                log.warn("과거 일정 페이지 수집 중단 ({}페이지째): {}", i + 1, e.toString());
                break;
            }
        }

        log.info("일정 수집 완료: {}건 (과거 페이지 포함)", all.size());
        return new ScheduleResponse(new ScheduleResponse.Data(
                new ScheduleResponse.Schedule(first.data().schedule().pages(), all)));
    }

    /**
     * 단순 지수 백오프: 연속 실패 시 base * 2^n (상한 max) 동안 해당 작업 스킵.
     */
    static class Backoff {
        private final long baseMs;
        private final long maxMs;
        private int failures = 0;
        private long nextAllowedAt = 0;

        Backoff(long baseMs, long maxMs) {
            this.baseMs = baseMs;
            this.maxMs = maxMs;
        }

        synchronized boolean allowed() {
            return System.currentTimeMillis() >= nextAllowedAt;
        }

        synchronized void success() {
            failures = 0;
            nextAllowedAt = 0;
        }

        synchronized void reset() {
            failures = 0;
            nextAllowedAt = 0;
        }

        synchronized void failure() {
            failures++;
            long delay = Math.min(maxMs, baseMs << Math.min(failures, 6));
            nextAllowedAt = System.currentTimeMillis() + delay;
        }

        synchronized int failures() {
            return failures;
        }

        synchronized java.util.Map<String, Object> status() {
            java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("consecutiveFailures", failures);
            out.put("blockedForMs", Math.max(0, nextAllowedAt - System.currentTimeMillis()));
            return out;
        }
    }
}
