package com.clutch.lolesports.repository;

import com.clutch.lolesports.entity.GamePlayerStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 적재된 세트 기록으로 시즌 누적 지표를 집계한다.
 *
 * 집계 대상은 finalized_at 이 채워진 세트뿐이다. 수집 중인 세트는 최종값이 아니라
 * 중간 프레임이 남아 있을 수 있어 누적에 섞으면 값이 흔들린다.
 *
 * KDA 나눗셈과 비율 계산은 SQL 이 아니라 Java 에서 수행한다. 여기서는 합계만 구해
 * 0 나눗셈과 소수 자릿수 처리를 한곳(SeasonStatsService)에 모은다.
 */
public interface SeasonStatsRepository extends JpaRepository<GamePlayerStat, Long> {

    /** 선수 한 명의 시즌 누적 합계. 팀 이동이 있으면 팀별로 행이 나뉜다 */
    interface PlayerTotals {
        String getSummonerName();
        String getTeamCode();
        Long getKills();
        Long getDeaths();
        Long getAssists();
        Long getGames();
    }

    /**
     * 챔피언 한 종의 시즌 누적.
     *
     * decidedPicks 는 세트 승자가 확정된 픽만 센다. 승자는 매치의 gameWins 증가분으로
     * 판정하므로 세트 종료 후 약 5분 뒤에야 채워진다(V8 참고). 이 값을 분모로 써야
     * 방금 끝난 세트가 패배로 잘못 집계되지 않는다.
     */
    interface ChampionTotals {
        String getChampionId();
        Long getPicks();
        Long getDecidedPicks();
        Long getWins();
    }

    @Query(value = """
            SELECT ps.summoner_name             AS summonerName,
                   mt.team_code                 AS teamCode,
                   SUM(COALESCE(ps.kills, 0))   AS kills,
                   SUM(COALESCE(ps.deaths, 0))  AS deaths,
                   SUM(COALESCE(ps.assists, 0)) AS assists,
                   COUNT(*)                     AS games
            FROM game_player_stat ps
            JOIN esports_game g       ON g.esports_game_id = ps.game_id
            JOIN esports_match m      ON m.esports_match_id = g.match_id
            LEFT JOIN match_team mt   ON mt.match_team_id = ps.match_team_id
            WHERE g.finalized_at IS NOT NULL
              AND m.season_key = :seasonKey
              AND ps.summoner_name IS NOT NULL
            GROUP BY ps.summoner_name, mt.team_code
            """, nativeQuery = true)
    List<PlayerTotals> playerTotals(@Param("seasonKey") String seasonKey);

    @Query(value = """
            SELECT ps.champion_id AS championId,
                   COUNT(*)       AS picks,
                   SUM(CASE WHEN g.winner_match_team_id IS NOT NULL
                            THEN 1 ELSE 0 END) AS decidedPicks,
                   SUM(CASE WHEN g.winner_match_team_id IS NOT NULL
                             AND g.winner_match_team_id = ps.match_team_id
                            THEN 1 ELSE 0 END) AS wins
            FROM game_player_stat ps
            JOIN esports_game g  ON g.esports_game_id = ps.game_id
            JOIN esports_match m ON m.esports_match_id = g.match_id
            WHERE g.finalized_at IS NOT NULL
              AND m.season_key = :seasonKey
              AND ps.champion_id IS NOT NULL
            GROUP BY ps.champion_id
            """, nativeQuery = true)
    List<ChampionTotals> championTotals(@Param("seasonKey") String seasonKey);

    /** 픽률의 분모. 프로 경기는 한 세트에 같은 챔피언이 중복되지 않아 픽 수의 상한이 된다 */
    @Query(value = """
            SELECT COUNT(*)
            FROM esports_game g
            JOIN esports_match m ON m.esports_match_id = g.match_id
            WHERE g.finalized_at IS NOT NULL
              AND m.season_key = :seasonKey
            """, nativeQuery = true)
    long finalizedGameCount(@Param("seasonKey") String seasonKey);

    /** 시즌 미지정 시 사용할 기본값. 적재된 시즌이 없으면 null */
    @Query(value = "SELECT MAX(m.season_key) FROM esports_match m", nativeQuery = true)
    String latestSeasonKey();
}
