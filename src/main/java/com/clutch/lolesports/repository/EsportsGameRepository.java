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
