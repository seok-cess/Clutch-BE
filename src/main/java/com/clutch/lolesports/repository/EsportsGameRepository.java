package com.clutch.lolesports.repository;

import com.clutch.lolesports.entity.EsportsGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EsportsGameRepository extends JpaRepository<EsportsGame, Long> {

    Optional<EsportsGame> findByExternalGameId(String externalGameId);

    List<EsportsGame> findByMatchIdOrderByGameNumberAsc(Long matchId);

    /** 적재가 끝난 세트만 — 과거 조회는 이것만 DB 로 응답한다 */
    Optional<EsportsGame> findByExternalGameIdAndFinalizedAtIsNotNull(String externalGameId);

    /**
     * livestats 종료는 확인했지만 아직 승자를 확정하지 못한 배팅 세트의 매치 ID를 찾는다.
     *
     * <p>세트 원본 상태({@code lifecycle_status})는 esports-api 반영 지연 때문에
     * {@code inProgress}로 남을 수 있다. 따라서 실제 종료 시각과 승자 미확정 여부를
     * 기준으로 결과 재조회 대상을 고른다.</p>
     */
    @Query(value = """
            select distinct match_row.external_match_id
            from esports_game game
            join esports_match match_row on match_row.esports_match_id = game.match_id
            join betting_event event on event.external_game_id = game.external_game_id
            where game.ended_at is not null
              and game.winner_decided_at is null
              and event.status in ('OPEN', 'CLOSED')
              and event.winner_external_team_id is null
            """, nativeQuery = true)
    List<String> findExternalMatchIdsPendingWinnerReconciliation();

    /** 재시작 후에도 이미 확정된 세트 승자를 외부 팀 ID로 복원한다. */
    @Query("""
            select team.externalTeamId
            from EsportsGame game, MatchTeam team
            where game.externalGameId = :externalGameId
              and game.winnerDecidedAt is not null
              and team.id = game.winnerMatchTeamId
            """)
    Optional<String> findWinnerExternalTeamId(@Param("externalGameId") String externalGameId);
}
