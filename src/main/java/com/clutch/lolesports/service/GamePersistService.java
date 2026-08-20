package com.clutch.lolesports.service;

import com.clutch.lolesports.config.LolesportsProperties;
import com.clutch.lolesports.dto.external.DetailsResponse;
import com.clutch.lolesports.dto.external.EventDetailsResponse;
import com.clutch.lolesports.dto.external.ScheduleResponse;
import com.clutch.lolesports.dto.external.WindowResponse;
import com.clutch.lolesports.entity.EsportsGame;
import com.clutch.lolesports.entity.EsportsMatch;
import com.clutch.lolesports.entity.GamePlayerStat;
import com.clutch.lolesports.entity.GameTimelinePoint;
import com.clutch.lolesports.entity.MatchTeam;
import com.clutch.lolesports.repository.EsportsGameRepository;
import com.clutch.lolesports.repository.EsportsMatchRepository;
import com.clutch.lolesports.repository.GamePlayerStatRepository;
import com.clutch.lolesports.repository.GameTimelinePointRepository;
import com.clutch.lolesports.repository.MatchTeamRepository;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 경기 종료 시 캐시의 최종값을 DB 로 적재한다.
 *
 * 캐시 프레임을 통째로 넣지 않는다. 수천 개 프레임 중
 *  - 마지막 프레임      → esports_game(팀 총계) + game_player_stat(선수 10행)
 *  - {@value #TIMELINE_STEP_SECONDS}초 간격 샘플 → game_timeline_point
 * 만 남기고 나머지는 버린다 (화면이 그 이상을 쓰지 않는다).
 *
 * 적재가 성공해야 캐시를 지울 수 있으므로 {@link #persistGame} 은 성공 여부를 반환한다.
 */
@Service
public class GamePersistService {

    private static final Logger log = LoggerFactory.getLogger(GamePersistService.class);

    /** 시계열 저장 간격. 화면 기본 step 이 10초라 그보다 촘촘히 저장해도 쓰이지 않는다 */
    private static final int TIMELINE_STEP_SECONDS = 10;

    private final DataCacheService cache;
    private final EsportsMatchRepository matchRepo;
    private final MatchTeamRepository matchTeamRepo;
    private final EsportsGameRepository gameRepo;
    private final GamePlayerStatRepository playerStatRepo;
    private final GameTimelinePointRepository timelineRepo;
    private final LolesportsProperties props;
    private final ObjectMapper objectMapper;
    private final SetWinnerTracker setWinners;

    public GamePersistService(DataCacheService cache,
                              EsportsMatchRepository matchRepo,
                              MatchTeamRepository matchTeamRepo,
                              EsportsGameRepository gameRepo,
                              GamePlayerStatRepository playerStatRepo,
                              GameTimelinePointRepository timelineRepo,
                              LolesportsProperties props,
                              ObjectMapper objectMapper,
                              SetWinnerTracker setWinners) {
        this.cache = cache;
        this.matchRepo = matchRepo;
        this.matchTeamRepo = matchTeamRepo;
        this.gameRepo = gameRepo;
        this.playerStatRepo = playerStatRepo;
        this.timelineRepo = timelineRepo;
        this.props = props;
        this.objectMapper = objectMapper;
        this.setWinners = setWinners;
    }

    /**
     * 세트 하나를 적재한다.
     *
     * @return 성공 여부. false 면 캐시를 지우지 말고 남겨둔다 (다음 기회에 재시도)
     */
    @Transactional
    public boolean persistGame(String externalGameId, MatchContext ctx) {
        try {
            WindowResponse.Frame lastFrame = cache.getNewestWindowFrame(externalGameId);
            if (lastFrame == null) {
                log.warn("적재 건너뜀 — window 프레임 없음 (gameId={})", externalGameId);
                return false;
            }

            EsportsMatch match = upsertMatch(ctx);
            Map<String, MatchTeam> teamsByExternalId = upsertMatchTeams(match, ctx);
            EsportsGame game = upsertGame(match, externalGameId, ctx, lastFrame, teamsByExternalId);

            persistPlayerStats(game, externalGameId, lastFrame, teamsByExternalId);
            int[] coverage = persistTimeline(game, externalGameId);

            game.markCollected(
                    EsportsGame.STATUS_COMPLETE,
                    cache.hasDetails(externalGameId)
                            ? EsportsGame.STATUS_COMPLETE : EsportsGame.STATUS_UNAVAILABLE,
                    coverage == null ? EsportsGame.STATUS_UNAVAILABLE : EsportsGame.STATUS_COMPLETE,
                    coverage == null ? null : coverage[0],
                    coverage == null ? null : coverage[1]);
            gameRepo.save(game);

            log.info("적재 완료 gameId={} (선수 {}행, 시계열 {}행)",
                    externalGameId,
                    playerStatRepo.findByGameIdOrderByParticipantNoAsc(game.getId()).size(),
                    timelineRepo.findByGameIdOrderByGameTimeSecondsAsc(game.getId()).size());
            return true;

        } catch (Exception e) {
            // 트랜잭션은 롤백되지만 캐시는 남으므로 다음 폴링에서 다시 시도된다
            log.error("적재 실패 gameId={}: {}", externalGameId, e.toString(), e);
            return false;
        }
    }

    /** 이미 적재가 끝난 세트인지 */
    @Transactional(readOnly = true)
    public boolean isAlreadyPersisted(String externalGameId) {
        return gameRepo.findByExternalGameIdAndFinalizedAtIsNotNull(externalGameId).isPresent();
    }

    /**
     * 진행 중인 매치와 참가 팀 메타데이터를 시청 세션보다 먼저 저장한다.
     *
     * <p>세트 종료 적재만 기다리면 첫 세트 시청 중에는 {@code watch_session}이 참조할
     * 매치 행이 없어 입장이 실패한다. 라이브 폴링 시점에 매치와 팀만 선저장하고,
     * 세트 통계는 기존 종료 적재 흐름에서 확정한다.</p>
     *
     * @param liveMatch 현재 라이브 매치 스냅샷
     */
    @Transactional
    public void persistLiveMatch(DataCacheService.LiveMatch liveMatch) {
        MatchContext context = MatchContext.of(
                liveMatch,
                liveMatch.activeGameId(),
                liveMatch.bestOf()
        );
        EsportsMatch match = upsertMatch(context);
        Map<String, MatchTeam> teamsByExternalId = upsertMatchTeams(match, context);
        persistLateDecidedWinners(liveMatch, teamsByExternalId);
    }

    /**
     * 세트 통계 적재 후 늦게 확정된 승자를 기존 esports_game 행에 반영한다.
     *
     * <p>livestats 종료 직후에는 gameWins가 아직 오르지 않아 승자 없이 적재될 수 있다.
     * 이후 라이브 메타 폴링에서 승자가 확인되면 이 경로가 같은 트랜잭션에서 보완해,
     * 서버 재시작 뒤에도 DB 기반 승자 복구가 가능하게 한다.</p>
     */
    private void persistLateDecidedWinners(
            DataCacheService.LiveMatch liveMatch,
            Map<String, MatchTeam> teamsByExternalId
    ) {
        if (liveMatch.games() == null) {
            return;
        }
        liveMatch.games().stream()
                .filter(game -> game.id() != null)
                .filter(game -> "completed".equalsIgnoreCase(game.state()))
                .forEach(game -> gameRepo.findByExternalGameId(game.id())
                        .ifPresent(persistedGame -> applyWinner(
                                persistedGame,
                                liveMatch.matchId(),
                                teamsByExternalId
                        )));
    }

    // ---- 매치 / 팀 ----

    private EsportsMatch upsertMatch(MatchContext ctx) {
        return matchRepo.findByExternalMatchId(ctx.matchId())
                .map(m -> {
                    m.updateProgress(ctx.state(), toLdt(ctx.startTime()), ctx.bestOf());
                    return m;
                })
                .orElseGet(() -> matchRepo.save(new EsportsMatch(
                        ctx.matchId(),
                        // 소스가 준 실제 리그를 쓴다. getLive 는 전 리그를 주므로
                        // 설정값을 그대로 박으면 타 리그 경기까지 LCK 로 저장된다.
                        ctx.leagueId() != null ? ctx.leagueId() : props.leagueId(),
                        seasonKeyOf(ctx.startTime()),
                        ctx.tournamentId() != null ? ctx.tournamentId() : props.tournamentId(),
                        ctx.blockName(),
                        toLdt(ctx.startTime()),
                        toLdt(ctx.startTime()),
                        ctx.state() != null ? ctx.state() : "completed",
                        ctx.bestOf())));
    }

    /** @return externalTeamId → MatchTeam (진영 판별용) */
    private Map<String, MatchTeam> upsertMatchTeams(EsportsMatch match, MatchContext ctx) {
        List<MatchTeam> existing = matchTeamRepo.findByMatchIdOrderByDisplayOrderAsc(match.getId());
        Map<String, MatchTeam> byExternalId = new HashMap<>();

        if (!existing.isEmpty()) {
            for (MatchTeam t : existing) {
                if (t.getExternalTeamId() != null) {
                    byExternalId.put(t.getExternalTeamId(), t);
                }
            }
            applyTeamResults(byExternalId, ctx);
            return byExternalId;
        }

        int order = 1;
        for (ScheduleResponse.Team team : ctx.teams()) {
            MatchTeam saved = matchTeamRepo.save(new MatchTeam(
                    match.getId(),
                    team.id(),
                    order++,
                    team.code(),
                    team.name(),
                    team.image(),
                    outcomeOf(team, ctx),
                    team.result() != null ? team.result().gameWins() : null,
                    team.record() != null ? team.record().wins() : null,
                    team.record() != null ? team.record().losses() : null));
            if (saved.getExternalTeamId() != null) {
                byExternalId.put(saved.getExternalTeamId(), saved);
            }
        }
        return byExternalId;
    }

    private void applyTeamResults(Map<String, MatchTeam> byExternalId, MatchContext ctx) {
        for (ScheduleResponse.Team team : ctx.teams()) {
            MatchTeam mt = team.id() != null ? byExternalId.get(team.id()) : null;
            if (mt != null && team.result() != null) {
                mt.updateResult(outcomeOf(team, ctx), team.result().gameWins());
            }
        }
    }

    /**
     * 매치 승패.
     *
     * 소스가 outcome 을 채우지 않고 gameWins 만 주는 경우가 있다
     * (2026-08-19 실측: 8/11~8/18 LCK 7경기가 completed 인데 outcome=null).
     * 그대로 두면 순위표 승수가 실제보다 적게 나오므로, 과반 세트를 가져간 팀이
     * 있으면 그것으로 승패를 유도한다. 판정 기준은 matchStateOf 와 같다.
     *
     * 진행 중인 매치는 유도하지 않는다 — Bo3 의 1:0 은 아직 승부가 나지 않았다.
     *
     * @return 소스 값이 있으면 그대로, 없고 과반이 갈렸으면 win/loss, 아니면 null
     */
    private static String outcomeOf(ScheduleResponse.Team team, MatchContext ctx) {
        String given = team.result() != null ? team.result().outcome() : null;
        if (given != null && !given.isBlank()) {
            return given;
        }
        int needed = (ctx.bestOf() == null ? 1 : ctx.bestOf()) / 2 + 1;
        boolean decided = ctx.teams().stream()
                .anyMatch(t -> t.result() != null && t.result().gameWins() != null
                        && t.result().gameWins() >= needed);
        if (!decided) {
            return null;
        }
        Integer wins = team.result() != null ? team.result().gameWins() : null;
        return wins != null && wins >= needed ? "win" : "loss";
    }

    // ---- 세트 ----

    private EsportsGame upsertGame(EsportsMatch match, String externalGameId, MatchContext ctx,
                                   WindowResponse.Frame lastFrame,
                                   Map<String, MatchTeam> teamsByExternalId) {
        EsportsGame game = gameRepo.findByExternalGameId(externalGameId)
                .orElseGet(() -> gameRepo.save(new EsportsGame(
                        externalGameId, match.getId(), ctx.gameNumber(), "completed")));
        game.markLifecycle("completed");

        WindowResponse.GameMetadata meta = cache.getWindowMeta(externalGameId);

        // 진영 판별 — 스코어보드의 esportsTeamId 를 match_team 과 매칭한다.
        // 피드 메타가 없거나 매칭이 안 되면 getEventDetails 가 준 세트별 진영으로 보완한다.
        MatchTeam blue = meta != null && meta.blueTeamMetadata() != null
                ? teamsByExternalId.get(meta.blueTeamMetadata().esportsTeamId()) : null;
        MatchTeam red = meta != null && meta.redTeamMetadata() != null
                ? teamsByExternalId.get(meta.redTeamMetadata().esportsTeamId()) : null;
        if (blue == null) {
            blue = teamsByExternalId.get(ctx.blueTeamId());
        }
        if (red == null) {
            red = teamsByExternalId.get(ctx.redTeamId());
        }
        game.assignSides(blue != null ? blue.getId() : null, red != null ? red.getId() : null);

        Instant start = cache.getGameStart(externalGameId);
        Instant lastTs = parse(lastFrame.rfc460Timestamp());
        DetailsResponse.Frame lastDetails = cache.getNewestDetailsFrame(externalGameId);

        game.updateMeta(
                lastFrame.gameState(),
                meta != null ? meta.patchVersion() : null,
                toLdt(start),
                toLdt(lastTs),
                (start != null && lastTs != null)
                        ? (int) Duration.between(start, lastTs).getSeconds() : null,
                toLdt(lastTs),
                lastDetails != null ? toLdt(parse(lastDetails.rfc460Timestamp())) : null);

        if (lastFrame.blueTeam() != null) {
            WindowResponse.TeamFrame t = lastFrame.blueTeam();
            game.updateBlueTotals(t.totalGold(), t.totalKills(), t.towers(), t.inhibitors(), t.barons());
        }
        if (lastFrame.redTeam() != null) {
            WindowResponse.TeamFrame t = lastFrame.redTeam();
            game.updateRedTotals(t.totalGold(), t.totalKills(), t.towers(), t.inhibitors(), t.barons());
        }

        game.updateObjectives(buildObjectivesJson(externalGameId, start));
        applyWinner(game, ctx.matchId(), teamsByExternalId);
        return game;
    }

    /**
     * 세트 승자를 기록한다.
     *
     * 승자는 {@link SetWinnerTracker} 가 라이브 폴링 중 gameWins 증가분으로 판정해 둔 값이다.
     * 세트 종료보다 약 5분 늦게 확정되므로, 적재 시점에 아직 미확정일 수 있다.
     * 그 경우 컬럼을 비워 두고(정산이 신뢰하지 않도록) 이후 재적재에서 채운다.
     *
     * 진영 매핑(blue/red match_team)이 없으면 승자를 세트에 귀속할 수 없어 저장하지 않는다.
     */
    private void applyWinner(EsportsGame game, String externalMatchId,
                             Map<String, MatchTeam> teamsByExternalId) {
        if (game.isWinnerDecided()) {
            return;   // 이미 확정된 값은 덮어쓰지 않는다
        }
        String winnerExternalTeamId = setWinners.winnerOf(
                externalMatchId,
                game.getExternalGameId()
        );
        if (winnerExternalTeamId == null) {
            return;   // 아직 gameWins 가 오르지 않았다 — 다음 기회에 채운다
        }
        MatchTeam winner = teamsByExternalId.get(winnerExternalTeamId);
        if (winner == null) {
            log.warn("세트 승자({})를 match_team 과 매칭하지 못했다 (gameId={})",
                    winnerExternalTeamId, game.getExternalGameId());
            return;
        }
        if (!game.decideWinner(winner.getId(), LocalDateTime.now(ZoneOffset.UTC))) {
            log.warn("세트 승자({})가 이 세트의 진영에 없다 (gameId={}) — 진영 매핑 확인 필요",
                    winnerExternalTeamId, game.getExternalGameId());
        }
    }

    /**
     * 오브젝트 이벤트 배열을 만든다.
     * 피드에 이벤트 타임스탬프가 없어 프레임 간 개수 증가로 시점을 역산한다
     * (그래프 마커·용 툴팁이 이 값을 쓴다).
     */
    private String buildObjectivesJson(String gameId, Instant start) {
        List<WindowResponse.Frame> frames = cache.getWindowSeries(gameId, Instant.now(), 1);
        List<Map<String, Object>> out = new ArrayList<>();
        WindowResponse.Frame prev = null;

        for (WindowResponse.Frame f : frames) {
            if (prev != null) {
                Long t = elapsed(start, f.rfc460Timestamp());
                collectSide(out, t, "blue", prev.blueTeam(), f.blueTeam());
                collectSide(out, t, "red", prev.redTeam(), f.redTeam());
            }
            prev = f;
        }
        try {
            return objectMapper.writeValueAsString(out);
        } catch (Exception e) {
            log.warn("objectives_json 직렬화 실패 (gameId={}): {}", gameId, e.toString());
            return "[]";
        }
    }

    private static void collectSide(List<Map<String, Object>> out, Long t, String side,
                                    WindowResponse.TeamFrame before, WindowResponse.TeamFrame after) {
        if (before == null || after == null || t == null) {
            return;
        }
        List<String> pd = before.dragons() != null ? before.dragons() : List.of();
        List<String> cd = after.dragons() != null ? after.dragons() : List.of();
        for (int i = pd.size(); i < cd.size(); i++) {
            out.add(objective(t, side, "dragon", cd.get(i)));
        }
        addCount(out, t, side, "baron", before.barons(), after.barons());
        addCount(out, t, side, "tower", before.towers(), after.towers());
        addCount(out, t, side, "inhibitor", before.inhibitors(), after.inhibitors());
    }

    private static void addCount(List<Map<String, Object>> out, Long t, String side, String type,
                                 Integer before, Integer after) {
        if (before == null || after == null) {
            return;
        }
        for (int i = 0; i < after - before; i++) {
            out.add(objective(t, side, type, null));
        }
    }

    private static Map<String, Object> objective(Long t, String side, String type, String subtype) {
        Map<String, Object> m = new HashMap<>();
        m.put("gameTimeSeconds", t);
        m.put("side", side);
        m.put("type", type);
        m.put("subtype", subtype);
        return m;
    }

    // ---- 선수 최종 기록 ----

    private void persistPlayerStats(EsportsGame game, String externalGameId,
                                    WindowResponse.Frame lastFrame,
                                    Map<String, MatchTeam> teamsByExternalId) {
        // 재적재 시 중복 방지. flush 로 DELETE 를 먼저 내보내지 않으면 JPA 가 INSERT 를
        // 앞세워 uk_game_player_stat_game_participant 에 걸린다 (all=true 백필 실패 원인).
        playerStatRepo.deleteByGameId(game.getId());
        playerStatRepo.flush();

        WindowResponse.GameMetadata meta = cache.getWindowMeta(externalGameId);
        Map<Integer, WindowResponse.ParticipantMetadata> metaById = participantMeta(meta);
        Map<Integer, DetailsResponse.Participant> detailsById =
                detailsById(cache.getNewestDetailsFrame(externalGameId));

        List<GamePlayerStat> rows = new ArrayList<>();
        rows.addAll(mapSide(game, lastFrame.blueTeam(), "blue",
                meta != null ? meta.blueTeamMetadata() : null, teamsByExternalId, metaById, detailsById));
        rows.addAll(mapSide(game, lastFrame.redTeam(), "red",
                meta != null ? meta.redTeamMetadata() : null, teamsByExternalId, metaById, detailsById));

        playerStatRepo.saveAll(rows);
    }

    private List<GamePlayerStat> mapSide(EsportsGame game, WindowResponse.TeamFrame team, String side,
                                         WindowResponse.TeamMetadata teamMeta,
                                         Map<String, MatchTeam> teamsByExternalId,
                                         Map<Integer, WindowResponse.ParticipantMetadata> metaById,
                                         Map<Integer, DetailsResponse.Participant> detailsById) {
        if (team == null || team.participants() == null) {
            return List.of();
        }
        MatchTeam mt = teamMeta != null ? teamsByExternalId.get(teamMeta.esportsTeamId()) : null;

        List<GamePlayerStat> rows = new ArrayList<>();
        for (WindowResponse.ParticipantFrame p : team.participants()) {
            if (p.participantId() == null) {
                continue;
            }
            WindowResponse.ParticipantMetadata pm = metaById.get(p.participantId());
            DetailsResponse.Participant d = detailsById.get(p.participantId());

            rows.add(new GamePlayerStat(
                    game.getId(),
                    mt != null ? mt.getId() : null,
                    p.participantId(),
                    side,
                    pm != null ? pm.esportsPlayerId() : null,
                    pm != null ? pm.summonerName() : null,
                    pm != null ? pm.championId() : null,
                    pm != null ? pm.role() : null,
                    p.level(),
                    p.kills(),
                    p.deaths(),
                    p.assists(),
                    p.creepScore(),
                    p.totalGold(),
                    d != null ? d.totalGoldEarned() : null,
                    ratio(d != null ? d.killParticipation() : null),
                    ratio(d != null ? d.championDamageShare() : null),
                    d != null ? d.wardsPlaced() : null,
                    d != null ? d.wardsDestroyed() : null,
                    toJson(d != null ? d.items() : List.of(), "[]"),
                    perksJson(d)));
        }
        return rows;
    }

    // ---- 골드 추이 시계열 ----

    /** @return {시작 초, 종료 초} — 저장할 게 없으면 null */
    private int[] persistTimeline(EsportsGame game, String externalGameId) {
        // 위와 같은 이유로 DELETE 를 먼저 확정한다 (uk_timeline_game_time)
        timelineRepo.deleteByGameId(game.getId());
        timelineRepo.flush();

        Instant start = cache.getGameStart(externalGameId);
        if (start == null) {
            return null;
        }
        List<WindowResponse.Frame> frames =
                cache.getWindowSeries(externalGameId, Instant.now(), TIMELINE_STEP_SECONDS);

        List<GameTimelinePoint> points = new ArrayList<>();
        Integer from = null;
        Integer to = null;

        for (WindowResponse.Frame f : frames) {
            Long t = elapsed(start, f.rfc460Timestamp());
            if (t == null || t < 0 || f.blueTeam() == null || f.redTeam() == null) {
                continue;
            }
            Long blueGold = f.blueTeam().totalGold();
            Long redGold = f.redTeam().totalGold();
            if (blueGold == null || redGold == null) {
                continue;
            }
            int sec = t.intValue();
            points.add(new GameTimelinePoint(
                    game.getId(), sec, blueGold, redGold,
                    nz(f.blueTeam().totalKills()), nz(f.redTeam().totalKills())));
            if (from == null) {
                from = sec;
            }
            to = sec;
        }

        if (points.isEmpty()) {
            return null;
        }
        timelineRepo.saveAll(points);
        return new int[]{from, to};
    }

    // ---- 유틸 ----

    private static Map<Integer, WindowResponse.ParticipantMetadata> participantMeta(
            WindowResponse.GameMetadata meta) {
        Map<Integer, WindowResponse.ParticipantMetadata> out = new HashMap<>();
        if (meta == null) {
            return out;
        }
        for (WindowResponse.TeamMetadata tm : new WindowResponse.TeamMetadata[]{
                meta.blueTeamMetadata(), meta.redTeamMetadata()}) {
            if (tm == null || tm.participantMetadata() == null) {
                continue;
            }
            for (WindowResponse.ParticipantMetadata pm : tm.participantMetadata()) {
                if (pm.participantId() != null) {
                    out.put(pm.participantId(), pm);
                }
            }
        }
        return out;
    }

    private static Map<Integer, DetailsResponse.Participant> detailsById(DetailsResponse.Frame frame) {
        Map<Integer, DetailsResponse.Participant> out = new HashMap<>();
        if (frame == null || frame.participants() == null) {
            return out;
        }
        for (DetailsResponse.Participant p : frame.participants()) {
            if (p.participantId() != null) {
                out.put(p.participantId(), p);
            }
        }
        return out;
    }

    private String perksJson(DetailsResponse.Participant d) {
        // CHECK 제약이 OBJECT 를 요구하므로 배열이 아니라 객체로 감싼다
        Map<String, Object> m = new HashMap<>();
        m.put("perks", d != null && d.perkMetadata() != null ? d.perkMetadata().perks() : List.of());
        if (d != null && d.perkMetadata() != null) {
            m.put("styleId", d.perkMetadata().styleId());
            m.put("subStyleId", d.perkMetadata().subStyleId());
        }
        return toJson(m, "{}");
    }

    private String toJson(Object value, String fallback) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    /** DECIMAL(7,6) — 0~1 범위를 벗어나면 CHECK 제약에 걸리므로 잘라낸다 */
    private static BigDecimal ratio(Double v) {
        if (v == null) {
            return null;
        }
        double clamped = Math.max(0d, Math.min(1d, v));
        return BigDecimal.valueOf(clamped).setScale(6, RoundingMode.HALF_UP);
    }

    private static Long elapsed(Instant start, String frameTs) {
        Instant ts = parse(frameTs);
        if (start == null || ts == null) {
            return null;
        }
        return Duration.between(start, ts).getSeconds();
    }

    private static Instant parse(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime toLdt(Instant i) {
        return i == null ? null : LocalDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    private static LocalDateTime toLdt(String rfc3339) {
        return toLdt(parse(rfc3339));
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /** 시즌 키 — H2H 집계 단위. 경기 시작 연도를 쓴다 */
    private static String seasonKeyOf(String startTime) {
        Instant i = parse(startTime);
        return String.valueOf((i != null ? i.atZone(ZoneOffset.UTC) : Instant.now().atZone(ZoneOffset.UTC)).getYear());
    }

    /**
     * 적재에 필요한 매치 정보. 캐시의 LiveMatch 나 일정 응답에서 만든다.
     */
    public record MatchContext(
            String matchId,
            String leagueName,
            /** 실제 리그 식별자 — getEventDetails 기준. null 이면 설정값으로 폴백 */
            String leagueId,
            /** 실제 대회 식별자 — 스플릿마다 다르다. null 이면 설정값으로 폴백 */
            String tournamentId,
            String blockName,
            String startTime,
            String state,
            Integer bestOf,
            Integer gameNumber,
            List<ScheduleResponse.Team> teams,
            /** 이 세트의 블루 진영 팀 id — getEventDetails 기준 (피드 메타가 없을 때 사용) */
            String blueTeamId,
            /** 이 세트의 레드 진영 팀 id */
            String redTeamId
    ) {
        /** 라이브 매치 + 세트 번호로 만든다 (리그·대회는 설정값 폴백) */
        public static MatchContext of(DataCacheService.LiveMatch m, String gameId, Integer bestOf) {
            return of(m, gameId, bestOf, null);
        }

        /**
         * 라이브 매치 + 세트 번호로 만든다.
         *
         * @param origin getEventDetails 로 확인한 실제 리그·대회. null 이면 설정값을 쓴다
         */
        public static MatchContext of(DataCacheService.LiveMatch m, String gameId, Integer bestOf,
                                      HistoricalGameService.MatchOrigin origin) {
            int number = 1;
            String blueTeamId = null;
            String redTeamId = null;
            if (m.games() != null) {
                for (EventDetailsResponse.Game g : m.games()) {
                    if (gameId == null || !gameId.equals(g.id())) {
                        continue;
                    }
                    if (g.number() != null) {
                        number = g.number();
                    }
                    // 진영은 세트마다 바뀐다 — 이 세트의 값을 쓴다
                    if (g.teams() != null) {
                        for (EventDetailsResponse.GameTeam t : g.teams()) {
                            if ("blue".equalsIgnoreCase(t.side())) {
                                blueTeamId = t.id();
                            } else if ("red".equalsIgnoreCase(t.side())) {
                                redTeamId = t.id();
                            }
                        }
                    }
                }
            }
            List<ScheduleResponse.Team> teams = m.teams() != null ? m.teams() : List.of();
            return new MatchContext(m.matchId(), m.leagueName(),
                    origin != null ? origin.leagueId() : null,
                    origin != null ? origin.tournamentId() : null,
                    m.blockName(), m.startTime(),
                    matchStateOf(teams, bestOf), bestOf, number, teams, blueTeamId, redTeamId);
        }

        /**
         * 매치 상태를 세트 득점으로 판정한다.
         *
         * 이 팩터리는 "세트 하나가 끝날 때"마다 호출된다. 예전에는 여기서 매치 상태를
         * "completed" 로 고정해, Bo3 의 1세트만 끝나도 매치 전체가 종료로 저장됐다.
         * 그 값을 시청 세션 입장 검사(WatchSessionService)가 읽고 있어 2·3세트
         * 입장이 막혔다.
         *
         * 매치 종료는 "최종 승리 팀이 결정된 시점" 이므로, 어느 팀이든 과반
         * (bestOf/2 + 1) 세트를 가져갔을 때만 completed 로 본다. 그 전까지는
         * 다음 세트를 기다리는 중이라도 inProgress 다.
         *
         * gameWins 는 세트 종료보다 약 5분 늦게 오르므로(2026-08-13 실측) 매치 종료
         * 판정도 그만큼 늦다. 소스 제약이라 우회할 수 없다.
         */
        private static String matchStateOf(List<ScheduleResponse.Team> teams, Integer bestOf) {
            int needed = (bestOf == null ? 1 : bestOf) / 2 + 1;
            for (ScheduleResponse.Team t : teams) {
                if (t.result() != null && t.result().gameWins() != null
                        && t.result().gameWins() >= needed) {
                    return "completed";
                }
            }
            return "inProgress";
        }
    }
}
