package com.clutch.lolesports.service;

import com.clutch.lolesports.api.ApiDtos;
import com.clutch.lolesports.entity.EsportsGame;
import com.clutch.lolesports.entity.GamePlayerStat;
import com.clutch.lolesports.entity.GameTimelinePoint;
import com.clutch.lolesports.entity.MatchTeam;
import com.clutch.lolesports.repository.EsportsGameRepository;
import com.clutch.lolesports.repository.GamePlayerStatRepository;
import com.clutch.lolesports.repository.GameTimelinePointRepository;
import com.clutch.lolesports.repository.MatchTeamRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 적재가 끝난 과거 경기를 DB 에서 읽어 화면 DTO 로 만든다.
 *
 * 이 경로가 있어야 과거 경기 조회 때 소스를 다시 부르지 않는다
 * (재수집은 세트당 수백 요청, 약 1.5분 걸린다).
 * 적재 전 경기는 여기서 빈 값을 반환하고, 호출 측이 기존 캐시 경로로 넘어간다.
 */
@Service
public class GameQueryService {

    private static final Logger log = LoggerFactory.getLogger(GameQueryService.class);

    private final EsportsGameRepository gameRepo;
    private final GamePlayerStatRepository playerStatRepo;
    private final GameTimelinePointRepository timelineRepo;
    private final MatchTeamRepository matchTeamRepo;
    private final ObjectMapper objectMapper;

    public GameQueryService(EsportsGameRepository gameRepo,
                            GamePlayerStatRepository playerStatRepo,
                            GameTimelinePointRepository timelineRepo,
                            MatchTeamRepository matchTeamRepo,
                            ObjectMapper objectMapper) {
        this.gameRepo = gameRepo;
        this.playerStatRepo = playerStatRepo;
        this.timelineRepo = timelineRepo;
        this.matchTeamRepo = matchTeamRepo;
        this.objectMapper = objectMapper;
    }

    /** 적재 완료된 세트만 응답한다 (부분 적재 상태를 화면에 노출하지 않기 위해) */
    @Transactional(readOnly = true)
    public Optional<ApiDtos.Scoreboard> scoreboard(String externalGameId) {
        return gameRepo.findByExternalGameIdAndFinalizedAtIsNotNull(externalGameId)
                .map(this::toScoreboard);
    }

    @Transactional(readOnly = true)
    public Optional<ApiDtos.GameDetails> details(String externalGameId) {
        return gameRepo.findByExternalGameIdAndFinalizedAtIsNotNull(externalGameId)
                .map(this::toDetails);
    }

    @Transactional(readOnly = true)
    public Optional<ApiDtos.GameHistory> history(String externalGameId, int stepSeconds) {
        return gameRepo.findByExternalGameIdAndFinalizedAtIsNotNull(externalGameId)
                .map(game -> toHistory(game, stepSeconds));
    }

    // ---- 변환 ----

    private ApiDtos.Scoreboard toScoreboard(EsportsGame game) {
        List<GamePlayerStat> players = playerStatRepo.findByGameIdOrderByParticipantNoAsc(game.getId());

        Long goldDiff = (game.getBlueTotalGold() != null && game.getRedTotalGold() != null)
                ? game.getBlueTotalGold() - game.getRedTotalGold() : null;

        return new ApiDtos.Scoreboard(
                game.getExternalGameId(),
                game.getFinalWindowFrameAt() != null ? game.getFinalWindowFrameAt().toString() : null,
                game.getTelemetryState(),
                game.getPatchVersion(),
                game.getDurationSeconds() != null ? game.getDurationSeconds().longValue() : null,
                goldDiff,
                teamScoreboard(game, players, "blue"),
                teamScoreboard(game, players, "red"));
    }

    private ApiDtos.TeamScoreboard teamScoreboard(EsportsGame game, List<GamePlayerStat> all, String side) {
        boolean blue = "blue".equals(side);
        Long matchTeamId = blue ? game.getBlueMatchTeamId() : game.getRedMatchTeamId();

        String esportsTeamId = matchTeamId == null ? null
                : matchTeamRepo.findById(matchTeamId).map(MatchTeam::getExternalTeamId).orElse(null);

        List<ApiDtos.PlayerRow> rows = all.stream()
                .filter(p -> side.equals(p.getSide()))
                .map(p -> new ApiDtos.PlayerRow(
                        p.getParticipantNo(),
                        p.getSummonerName(),
                        p.getChampionId(),
                        p.getRole(),
                        p.getLevel(),
                        p.getKills(),
                        p.getDeaths(),
                        p.getAssists(),
                        p.getCreepScore(),
                        p.getTotalGold(),
                        null,   // currentHealth — 종료된 경기에는 의미가 없다
                        null))
                .toList();

        return new ApiDtos.TeamScoreboard(
                esportsTeamId,
                blue ? game.getBlueTotalGold() : game.getRedTotalGold(),
                blue ? game.getBlueTotalKills() : game.getRedTotalKills(),
                blue ? game.getBlueTowers() : game.getRedTowers(),
                blue ? game.getBlueInhibitors() : game.getRedInhibitors(),
                blue ? game.getBlueBarons() : game.getRedBarons(),
                dragonsOf(game, side),
                rows);
    }

    /** objectives_json 에서 해당 진영의 용 종류를 획득 순서대로 뽑는다 */
    private List<String> dragonsOf(EsportsGame game, String side) {
        return objectives(game).stream()
                .filter(o -> "dragon".equals(o.type()) && side.equals(o.side()))
                .map(ApiDtos.ObjectiveEvent::subtype)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private ApiDtos.GameDetails toDetails(EsportsGame game) {
        List<ApiDtos.PlayerDetail> players =
                playerStatRepo.findByGameIdOrderByParticipantNoAsc(game.getId()).stream()
                        .map(p -> new ApiDtos.PlayerDetail(
                                p.getParticipantNo(),
                                p.getSummonerName(),
                                p.getChampionId(),
                                toDouble(p.getKillParticipationRatio()),
                                toDouble(p.getChampionDamageShareRatio()),
                                p.getWardsPlaced(),
                                p.getWardsDestroyed(),
                                p.getTotalGoldEarned(),
                                readLongList(p.getItemsJson()),
                                readPerks(p.getPerksJson())))
                        .toList();

        return new ApiDtos.GameDetails(
                game.getExternalGameId(),
                game.getFinalDetailsFrameAt() != null ? game.getFinalDetailsFrameAt().toString() : null,
                players);
    }

    private ApiDtos.GameHistory toHistory(EsportsGame game, int stepSeconds) {
        List<GameTimelinePoint> saved = timelineRepo.findByGameIdOrderByGameTimeSecondsAsc(game.getId());

        // 저장 간격(10초)보다 성긴 요청이면 그만큼 솎아낸다
        List<ApiDtos.HistoryPoint> points = new ArrayList<>();
        Integer next = null;
        for (GameTimelinePoint p : saved) {
            if (next != null && p.getGameTimeSeconds() < next) {
                continue;
            }
            points.add(new ApiDtos.HistoryPoint(
                    p.getGameTimeSeconds().longValue(),
                    p.goldDiff(),
                    p.getBlueGold(),
                    p.getRedGold(),
                    p.getBlueKills(),
                    p.getRedKills()));
            next = p.getGameTimeSeconds() + Math.max(1, stepSeconds);
        }

        return new ApiDtos.GameHistory(game.getExternalGameId(), points, objectives(game));
    }

    private List<ApiDtos.ObjectiveEvent> objectives(EsportsGame game) {
        if (game.getObjectivesJson() == null || game.getObjectivesJson().isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(
                    game.getObjectivesJson(), new TypeReference<>() {
                    });
            return raw.stream()
                    .map(m -> new ApiDtos.ObjectiveEvent(
                            m.get("gameTimeSeconds") instanceof Number n ? n.longValue() : null,
                            (String) m.get("side"),
                            (String) m.get("type"),
                            (String) m.get("subtype")))
                    .toList();
        } catch (Exception e) {
            log.warn("objectives_json 파싱 실패 (gameId={}): {}", game.getExternalGameId(), e.toString());
            return List.of();
        }
    }

    private List<Long> readLongList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    /** perks_json 은 {"perks":[...]} 객체 — 화면은 배열만 쓴다 */
    @SuppressWarnings("unchecked")
    private List<Long> readPerks(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> m = objectMapper.readValue(json, new TypeReference<>() {
            });
            Object perks = m.get("perks");
            if (perks instanceof List<?> list) {
                return list.stream()
                        .filter(v -> v instanceof Number)
                        .map(v -> ((Number) v).longValue())
                        .toList();
            }
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    private static Double toDouble(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }
}
