package com.clutch.lolesports.api;

import com.clutch.betting.service.BettingCandidateQueryService;
import com.clutch.lolesports.dto.external.DetailsResponse;
import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.dto.external.ScheduleResponse;
import com.clutch.lolesports.dto.external.StandingsResponse;
import com.clutch.lolesports.dto.external.WindowResponse;
import com.clutch.lolesports.config.LolesportsProperties;
import com.clutch.lolesports.service.DataCacheService;
import com.clutch.lolesports.service.HistoricalGameService;
import com.clutch.lolesports.source.ExternalSourceMode;
import com.clutch.lolesports.source.ExternalSourceState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 프론트 소비용 REST API. 전부 인메모리 캐시에서 응답한다 (외부 소스 직접 호출 없음).
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final DataCacheService cache;
    private final HistoricalGameService historical;
    private final LolesportsProperties props;
    private final com.clutch.lolesports.client.LiveStatsClient liveStats;
    private final com.clutch.lolesports.service.TeamRecordService records;
    private final com.clutch.lolesports.service.GameQueryService gameQuery;
    private final com.clutch.lolesports.service.SetWinnerTracker setWinners;
    private final com.clutch.lolesports.service.SeasonStatsService seasonStats;
    private final com.clutch.lolesports.service.PollingScheduler polling;
    private final ExternalSourceState sourceState;
    private final BettingCandidateQueryService bettingCandidateQueryService;

    public ApiController(DataCacheService cache, HistoricalGameService historical,
                         LolesportsProperties props, com.clutch.lolesports.client.LiveStatsClient liveStats,
                         com.clutch.lolesports.service.TeamRecordService records,
                         com.clutch.lolesports.service.GameQueryService gameQuery,
                         com.clutch.lolesports.service.SetWinnerTracker setWinners,
                         com.clutch.lolesports.service.SeasonStatsService seasonStats,
                         com.clutch.lolesports.service.PollingScheduler polling,
                         ExternalSourceState sourceState,
                         BettingCandidateQueryService bettingCandidateQueryService) {
        this.cache = cache;
        this.historical = historical;
        this.props = props;
        this.liveStats = liveStats;
        this.records = records;
        this.gameQuery = gameQuery;
        this.setWinners = setWinners;
        this.seasonStats = seasonStats;
        this.polling = polling;
        this.sourceState = sourceState;
        this.bettingCandidateQueryService = bettingCandidateQueryService;
    }

    /**
     * lag 파라미터 규칙:
     *  - lag <= 0 : 최신 우선 모드 — 버퍼의 가장 새 프레임. 지연은 최소지만 블록 단위(10초)로 점프한다
     *  - lag > 0  : 재생 모드 — now-lag 시점 프레임. 폴링마다(1초) 값이 전진한다
     *  - 생략     : 재생 모드, lag 은 소스가 요구하는 최소 지연 + 여유로 자동 결정
     *
     * 기본을 재생 모드로 두는 이유: 소스가 10초 블록으로 초 단위 프레임 수십 개를 한꺼번에 주므로,
     * 최신만 보면 그 안의 초 단위 변화가 전부 버려진다 (2026-08-08 라이브 실측).
     */
    private WindowResponse.Frame pickWindowFrame(String gameId, Long lag) {
        if (sourceState.mode() == ExternalSourceMode.STUB || (lag != null && lag <= 0)) {
            return cache.getNewestWindowFrame(gameId);
        }
        return cache.getWindowFrameAt(gameId, displayTarget(lag));
    }

    private DetailsResponse.Frame pickDetailsFrame(String gameId, Long lag) {
        if (sourceState.mode() == ExternalSourceMode.STUB || (lag != null && lag <= 0)) {
            return cache.getNewestDetailsFrame(gameId);
        }
        return cache.getDetailsFrameAt(gameId, displayTarget(lag));
    }

    /**
     * 재생 시점. lag 미지정 시 소스가 현재 요구하는 최소 지연에 재생 여유를 더해 자동 결정한다
     * (소스 요구치는 경기마다 다르고 실행 중에도 올라갈 수 있어 하드코딩하면 화면이 멈춘다).
     */
    /**
     * 표시 중인 프레임의 게임 경과 시간(초).
     * 피드에 게임 시계 필드가 없어 "프레임 시각 - 게임 시작 시각"으로 계산한다.
     */
    private Long elapsedSeconds(String gameId, String frameTs) {
        java.time.Instant start = cache.getGameStart(gameId);
        if (start == null || frameTs == null) {
            return null;
        }
        try {
            long sec = java.time.Duration.between(start, java.time.Instant.parse(frameTs)).getSeconds();
            return sec >= 0 ? sec : null;
        } catch (Exception e) {
            return null;
        }
    }

    private java.time.Instant displayTarget(Long lagOverride) {
        long lag = lagOverride != null
                ? Math.max(10, Math.min(300, lagOverride))
                : liveStats.currentLagSeconds() + props.displayLagSeconds();
        return java.time.Instant.now().minusSeconds(lag);
    }

    // ---- 일정/결과 ----

    @GetMapping("/schedule")
    public ResponseEntity<List<ApiDtos.ScheduleItem>> schedule() {
        ScheduleResponse cached = cache.getSchedule();
        if (cached == null || cached.data() == null || cached.data().schedule() == null
                || cached.data().schedule().events() == null) {
            return ResponseEntity.ok(List.of()); // 아직 첫 폴링 전
        }
        List<ApiDtos.ScheduleItem> items = cached.data().schedule().events().stream()
                .filter(e -> "match".equalsIgnoreCase(e.type()) && e.match() != null)
                .map(e -> new ApiDtos.ScheduleItem(
                        e.startTime(),
                        e.state(),
                        e.blockName(),
                        e.match().id(),
                        e.match().strategy() != null ? e.match().strategy().count() : null,
                        mapTeams(e.match().teams())
                ))
                .toList();
        return ResponseEntity.ok(items);
    }

    // ---- 순위 ----

    @GetMapping("/standings")
    public ResponseEntity<List<ApiDtos.StandingsSection>> standings() {
        StandingsResponse cached = cache.getStandings();
        List<ApiDtos.StandingsSection> out = new ArrayList<>();
        if (cached == null || cached.data() == null || cached.data().standings() == null) {
            return ResponseEntity.ok(out);
        }
        for (StandingsResponse.Standing standing : cached.data().standings()) {
            if (standing.stages() == null) continue;
            for (StandingsResponse.Stage stage : standing.stages()) {
                if (stage.sections() == null) continue;
                for (StandingsResponse.Section section : stage.sections()) {
                    if (section.rankings() == null || section.rankings().isEmpty()) continue;
                    List<ApiDtos.RankingRow> rows = section.rankings().stream()
                            .map(r -> new ApiDtos.RankingRow(
                                    r.ordinal(),
                                    r.teams() == null ? List.of() : r.teams().stream()
                                            .map(t -> new ApiDtos.RankedTeam(
                                                    t.name(), t.code(), t.image(),
                                                    t.record() != null ? t.record().wins() : null,
                                                    t.record() != null ? t.record().losses() : null))
                                            .toList()
                            ))
                            .toList();
                    out.add(new ApiDtos.StandingsSection(stage.name(), section.name(), rows));
                }
            }
        }
        return ResponseEntity.ok(out);
    }

    /**
     * 리그 팀 순위표 (매치 기준).
     *
     * 대회(스플릿) 목록을 쿼리로 받는다. LCK 는 한 시즌이 여러 스플릿으로 나뉘는데
     * 무엇을 합쳐 보여줄지는 화면 판단이라 서버가 고정하지 않는다.
     * 미지정이면 설정의 현재 대회 하나만 집계한다.
     *
     * 예: /api/standings/teams?tournamentIds=115548128960088078,115548147890329817
     */
    @GetMapping("/standings/teams")
    public ResponseEntity<ApiDtos.TeamStandingsBoard> teamStandings(
            @org.springframework.web.bind.annotation.RequestParam(value = "season", required = false) String season,
            @org.springframework.web.bind.annotation.RequestParam(value = "leagueId", required = false) String leagueId,
            @org.springframework.web.bind.annotation.RequestParam(value = "tournamentIds", required = false) List<String> tournamentIds) {
        String league = (leagueId == null || leagueId.isBlank()) ? props.leagueId() : leagueId;
        List<String> tournaments = (tournamentIds == null || tournamentIds.isEmpty())
                ? List.of(props.tournamentId()) : tournamentIds;
        return ResponseEntity.ok(
                seasonStats.teamStandings(season, league, tournaments, groupByTeamCode()));
    }

    /**
     * 팀 코드 → 소속 그룹명 (예: GEN → "레전드 그룹").
     *
     * LCK 는 정규시즌을 두 그룹으로 나눠 운영하는데 그 편성은 우리가 판단할 수 없다.
     * 캐시된 getStandingsV3 응답이 조 편성을 담고 있어 거기서 가져온다.
     * 순위표가 아직 캐시되지 않았으면 빈 맵 — 그때는 단일 순위표로 응답한다.
     */
    private java.util.Map<String, String> groupByTeamCode() {
        StandingsResponse cached = cache.getStandings();
        if (cached == null || cached.data() == null || cached.data().standings() == null) {
            return java.util.Map.of();
        }
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        for (StandingsResponse.Standing standing : cached.data().standings()) {
            if (standing.stages() == null) continue;
            for (StandingsResponse.Stage stage : standing.stages()) {
                if (stage.sections() == null) continue;
                for (StandingsResponse.Section section : stage.sections()) {
                    if (section.rankings() == null || section.name() == null) continue;
                    for (StandingsResponse.Ranking ranking : section.rankings()) {
                        if (ranking.teams() == null) continue;
                        for (StandingsResponse.Team team : ranking.teams()) {
                            if (team.code() != null) {
                                out.putIfAbsent(team.code(), section.name());
                            }
                        }
                    }
                }
            }
        }
        return out;
    }

    // ---- 라이브 요약 ----

    @GetMapping("/live")
    public ResponseEntity<ApiDtos.LiveSummary> live() {
        List<ApiDtos.LiveMatchItem> items = cache.getLiveMatches().stream()
                .map(this::toLiveMatchItem)
                .toList();
        return ResponseEntity.ok(new ApiDtos.LiveSummary(!items.isEmpty(), items));
    }

    /** 시작 전에도 실제로 배팅이 열린 매치를 라이브 화면의 배팅 카드용으로 반환한다. */
    @GetMapping("/betting-candidates")
    public ResponseEntity<List<ApiDtos.LiveMatchItem>> bettingCandidates() {
        return ResponseEntity.ok(bettingCandidateQueryService.findOpenMatchCandidates().stream()
                .map(this::toLiveMatchItem)
                .toList());
    }

    /** 캐시된 매치를 라이브와 시작 전 배팅 카드가 함께 쓰는 응답 모델로 변환한다. */
    private ApiDtos.LiveMatchItem toLiveMatchItem(DataCacheService.LiveMatch match) {
        return new ApiDtos.LiveMatchItem(
                match.matchId(),
                match.leagueName(),
                match.blockName(),
                match.startTime(),
                match.bestOf(),
                match.isFinished(),
                match.winnerTeamId(),
                mapTeams(match.teams()),
                match.games() == null ? List.of() : match.games().stream()
                        .map(game -> new ApiDtos.GameItem(
                                game.id(),
                                game.number(),
                                game.state(),
                                cache.isFeedFinished(game.id()),
                                setWinners.winnerOf(match.matchId(), game.id()),
                                polling.isStatsUnavailable(game.id())))
                        .toList(),
                match.activeGameId()
        );
    }

    // ---- 전적 (최근 폼 / 상대 전적) ----

    /** 팀별 최근 5경기 결과. 키는 팀 코드 */
    @GetMapping("/records/recent")
    public ResponseEntity<Map<String, List<ApiDtos.RecentMatch>>> recentForm() {
        return ResponseEntity.ok(records.recentFormByTeam());
    }

    /** 두 팀의 상대 전적 (예: /api/records/h2h?a=T1&b=HLE) */
    @GetMapping("/records/h2h")
    public ResponseEntity<ApiDtos.HeadToHead> headToHead(
            @org.springframework.web.bind.annotation.RequestParam String a,
            @org.springframework.web.bind.annotation.RequestParam String b) {
        return ResponseEntity.ok(records.headToHead(a, b));
    }

    // ---- 시즌 누적 집계 (메인 화면 요약 카드) ----

    /** 시즌 누적 KDA 상위 선수 (예: /api/stats/players/kda?limit=5) */
    @GetMapping("/stats/players/kda")
    public ResponseEntity<ApiDtos.PlayerKdaBoard> playerKda(
            @org.springframework.web.bind.annotation.RequestParam(value = "season", required = false) String season,
            @org.springframework.web.bind.annotation.RequestParam(value = "limit", defaultValue = "5") int limit) {
        return ResponseEntity.ok(seasonStats.playerKda(season, limit));
    }

    /** 시즌 챔피언 픽률·승률 (예: /api/stats/champions?limit=5) */
    @GetMapping("/stats/champions")
    public ResponseEntity<ApiDtos.ChampionBoard> champions(
            @org.springframework.web.bind.annotation.RequestParam(value = "season", required = false) String season,
            @org.springframework.web.bind.annotation.RequestParam(value = "limit", defaultValue = "5") int limit) {
        return ResponseEntity.ok(seasonStats.champions(season, limit));
    }

    // ---- 매치별 게임(세트) 목록 — 과거 경기 열람 진입점 ----

    @GetMapping("/matches/{matchId}/games")
    public ResponseEntity<List<ApiDtos.GameItem>> matchGames(@PathVariable String matchId) {
        List<EventDetailsResponse.Game> games = historical.getGames(matchId);
        return ResponseEntity.ok(games.stream()
                .map(g -> new ApiDtos.GameItem(
                        g.id(),
                        g.number(),
                        g.state(),
                        cache.isFeedFinished(g.id()),
                        setWinners.winnerOf(matchId, g.id()),
                        polling.isStatsUnavailable(g.id())))
                .toList());
    }

    /**
     * 매치의 팀 정보 (id 포함).
     * getSchedule 은 팀 id 를 주지 않아 진영(블루/레드) 판별이 불가능하므로,
     * id 가 있는 getEventDetails 쪽 정보를 별도로 노출한다.
     */
    @GetMapping("/matches/{matchId}/teams")
    public ResponseEntity<List<ApiDtos.ScheduleTeam>> matchTeams(@PathVariable String matchId) {
        return ResponseEntity.ok(mapTeams(historical.getTeams(matchId)));
    }

    // ---- 스코어보드 (라이브는 폴링 캐시, 과거 경기는 온디맨드 로드) ----

    @GetMapping("/live/{gameId}/scoreboard")
    public ResponseEntity<ApiDtos.Scoreboard> scoreboard(@PathVariable String gameId,
                                                         @org.springframework.web.bind.annotation.RequestParam(value = "lag", required = false) Long lag) {
        WindowResponse.Frame frame = pickWindowFrame(gameId, lag);
        if (frame == null) {
            // 적재가 끝난 과거 경기는 DB 로 응답한다 — 소스 재수집(세트당 ~1.5분)을 피한다
            var stored = gameQuery.scoreboard(gameId);
            if (stored.isPresent()) {
                return ResponseEntity.ok(stored.get());
            }
            historical.ensureGameLoaded(gameId);
            frame = pickWindowFrame(gameId, lag);
        }
        if (frame == null) {
            return ResponseEntity.notFound().build();
        }
        WindowResponse.GameMetadata meta = cache.getWindowMeta(gameId);

        ApiDtos.TeamScoreboard blue = mapTeamScoreboard(frame.blueTeam(),
                meta != null ? meta.blueTeamMetadata() : null);
        ApiDtos.TeamScoreboard red = mapTeamScoreboard(frame.redTeam(),
                meta != null ? meta.redTeamMetadata() : null);

        Long goldDiff = null;
        if (blue != null && red != null && blue.totalGold() != null && red.totalGold() != null) {
            goldDiff = blue.totalGold() - red.totalGold();
        }

        return ResponseEntity.ok(new ApiDtos.Scoreboard(
                gameId,
                frame.rfc460Timestamp(),
                frame.gameState(),
                meta != null ? meta.patchVersion() : null,
                elapsedSeconds(gameId, frame.rfc460Timestamp()),
                goldDiff,
                blue,
                red
        ));
    }

    // ---- 골드차 추이 (그래프용) ----

    @GetMapping("/live/{gameId}/history")
    public ResponseEntity<ApiDtos.GameHistory> history(
            @PathVariable String gameId,
            @org.springframework.web.bind.annotation.RequestParam(value = "lag", required = false) Long lag,
            @org.springframework.web.bind.annotation.RequestParam(value = "step", defaultValue = "10") int step) {
        if (!cache.hasWindow(gameId)) {
            var stored = gameQuery.history(gameId, Math.max(1, step));
            if (stored.isPresent()) {
                return ResponseEntity.ok(stored.get());
            }
            historical.ensureGameLoaded(gameId);
        }
        java.time.Instant until = (sourceState.mode() == ExternalSourceMode.STUB || (lag != null && lag <= 0))
                ? java.time.Instant.MAX
                : displayTarget(lag);

        java.time.Instant start = cache.getGameStart(gameId);
        List<ApiDtos.HistoryPoint> points = cache.getWindowSeries(gameId, until, Math.max(1, step)).stream()
                .map(f -> {
                    Long t = elapsed(start, f.rfc460Timestamp());
                    Long blue = f.blueTeam() != null ? f.blueTeam().totalGold() : null;
                    Long red = f.redTeam() != null ? f.redTeam().totalGold() : null;
                    return new ApiDtos.HistoryPoint(
                            t,
                            (blue != null && red != null) ? blue - red : null,
                            blue, red,
                            f.blueTeam() != null ? f.blueTeam().totalKills() : null,
                            f.redTeam() != null ? f.redTeam().totalKills() : null);
                })
                .filter(p -> p.goldDiff() != null)
                .toList();

        // 오브젝트는 원본 해상도(1초)로 훑어야 시점이 정확하다 — step 으로 솎으면 놓친다
        List<ApiDtos.ObjectiveEvent> objectives =
                detectObjectives(cache.getWindowSeries(gameId, until, 1), start);

        return ResponseEntity.ok(new ApiDtos.GameHistory(gameId, points, objectives));
    }

    /** 게임 시작 기준 경과 초 */
    private static Long elapsed(java.time.Instant start, String frameTs) {
        if (start == null || frameTs == null) {
            return null;
        }
        try {
            return java.time.Duration.between(start, java.time.Instant.parse(frameTs)).getSeconds();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 프레임 타임라인에서 오브젝트 획득 시점을 역산한다.
     * 피드가 이벤트를 주지 않으므로, 개수가 증가한 첫 프레임을 획득 시점으로 본다.
     */
    private static List<ApiDtos.ObjectiveEvent> detectObjectives(
            List<WindowResponse.Frame> frames, java.time.Instant start) {

        List<ApiDtos.ObjectiveEvent> out = new ArrayList<>();
        WindowResponse.Frame prev = null;

        for (WindowResponse.Frame f : frames) {
            if (prev != null) {
                Long t = elapsed(start, f.rfc460Timestamp());
                collectSide(out, t, "blue", prev.blueTeam(), f.blueTeam());
                collectSide(out, t, "red", prev.redTeam(), f.redTeam());
            }
            prev = f;
        }
        return out;
    }

    /** 한 팀의 이전/현재 프레임을 비교해 늘어난 오브젝트를 기록 */
    private static void collectSide(List<ApiDtos.ObjectiveEvent> out, Long t, String side,
                                    WindowResponse.TeamFrame before, WindowResponse.TeamFrame after) {
        if (before == null || after == null || t == null) {
            return;
        }

        // 용은 종류가 순서대로 쌓이므로 새로 추가된 항목만 꺼낸다
        List<String> pd = before.dragons() != null ? before.dragons() : List.of();
        List<String> cd = after.dragons() != null ? after.dragons() : List.of();
        for (int i = pd.size(); i < cd.size(); i++) {
            out.add(new ApiDtos.ObjectiveEvent(t, side, "dragon", cd.get(i)));
        }

        addCount(out, t, side, "baron", before.barons(), after.barons());
        addCount(out, t, side, "tower", before.towers(), after.towers());
        addCount(out, t, side, "inhibitor", before.inhibitors(), after.inhibitors());
    }

    private static void addCount(List<ApiDtos.ObjectiveEvent> out, Long t, String side,
                                 String type, Integer before, Integer after) {
        if (before == null || after == null) {
            return;
        }
        for (int i = 0; i < after - before; i++) {
            out.add(new ApiDtos.ObjectiveEvent(t, side, type, null));
        }
    }

    // ---- 선수 상세 (라이브는 폴링 캐시, 과거 경기는 온디맨드 로드) ----

    @GetMapping("/live/{gameId}/details")
    public ResponseEntity<ApiDtos.GameDetails> details(@PathVariable String gameId,
                                                       @org.springframework.web.bind.annotation.RequestParam(value = "lag", required = false) Long lag) {
        DetailsResponse.Frame frame = pickDetailsFrame(gameId, lag);
        if (frame == null) {
            var stored = gameQuery.details(gameId);
            if (stored.isPresent()) {
                return ResponseEntity.ok(stored.get());
            }
            historical.ensureGameLoaded(gameId);
            frame = pickDetailsFrame(gameId, lag);
        }
        if (frame == null) {
            return ResponseEntity.notFound().build();
        }

        // window 메타데이터로 소환사명/챔피언 보강
        Map<Integer, WindowResponse.ParticipantMetadata> metaById = participantMetaById(gameId);

        List<ApiDtos.PlayerDetail> players = frame.participants() == null ? List.of()
                : frame.participants().stream()
                .map(p -> {
                    WindowResponse.ParticipantMetadata pm = metaById.get(p.participantId());
                    return new ApiDtos.PlayerDetail(
                            p.participantId(),
                            pm != null ? pm.summonerName() : null,
                            pm != null ? pm.championId() : null,
                            p.killParticipation(),
                            p.championDamageShare(),
                            p.wardsPlaced(),
                            p.wardsDestroyed(),
                            p.totalGoldEarned(),
                            p.items(),
                            p.perkMetadata() != null ? p.perkMetadata().perks() : null
                    );
                })
                .toList();

        return ResponseEntity.ok(new ApiDtos.GameDetails(gameId, frame.rfc460Timestamp(), players));
    }

    // ---- 매핑 헬퍼 ----

    private static List<ApiDtos.ScheduleTeam> mapTeams(List<ScheduleResponse.Team> teams) {
        if (teams == null) {
            return List.of();
        }
        return teams.stream()
                .map(t -> new ApiDtos.ScheduleTeam(
                        t.id(),
                        t.name(),
                        t.code(),
                        t.image(),
                        t.result() != null ? t.result().outcome() : null,
                        t.result() != null ? t.result().gameWins() : null,
                        t.record() != null ? t.record().wins() : null,
                        t.record() != null ? t.record().losses() : null
                ))
                .toList();
    }

    private static ApiDtos.TeamScoreboard mapTeamScoreboard(WindowResponse.TeamFrame team,
                                                            WindowResponse.TeamMetadata meta) {
        if (team == null) {
            return null;
        }
        Map<Integer, WindowResponse.ParticipantMetadata> metaById = new HashMap<>();
        if (meta != null && meta.participantMetadata() != null) {
            for (WindowResponse.ParticipantMetadata pm : meta.participantMetadata()) {
                if (pm.participantId() != null) {
                    metaById.put(pm.participantId(), pm);
                }
            }
        }
        List<ApiDtos.PlayerRow> players = team.participants() == null ? List.of()
                : team.participants().stream()
                .map(p -> {
                    WindowResponse.ParticipantMetadata pm = metaById.get(p.participantId());
                    return new ApiDtos.PlayerRow(
                            p.participantId(),
                            pm != null ? pm.summonerName() : null,
                            pm != null ? pm.championId() : null,
                            pm != null ? pm.role() : null,
                            p.level(),
                            p.kills(),
                            p.deaths(),
                            p.assists(),
                            p.creepScore(),
                            p.totalGold(),
                            p.currentHealth(),
                            p.maxHealth()
                    );
                })
                .toList();

        return new ApiDtos.TeamScoreboard(
                meta != null ? meta.esportsTeamId() : null,
                team.totalGold(),
                team.totalKills(),
                team.towers(),
                team.inhibitors(),
                team.barons(),
                team.dragons(),
                players
        );
    }

    private Map<Integer, WindowResponse.ParticipantMetadata> participantMetaById(String gameId) {
        Map<Integer, WindowResponse.ParticipantMetadata> out = new HashMap<>();
        WindowResponse.GameMetadata meta = cache.getWindowMeta(gameId);
        if (meta == null) {
            return out;
        }
        for (WindowResponse.TeamMetadata tm : new WindowResponse.TeamMetadata[]{
                meta.blueTeamMetadata(), meta.redTeamMetadata()}) {
            if (tm == null || tm.participantMetadata() == null) continue;
            for (WindowResponse.ParticipantMetadata pm : tm.participantMetadata()) {
                if (pm.participantId() != null) {
                    out.put(pm.participantId(), pm);
                }
            }
        }
        return out;
    }
}
