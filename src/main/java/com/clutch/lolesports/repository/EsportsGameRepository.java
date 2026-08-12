package com.clutch.lolesports.repository;

import com.clutch.lolesports.entity.EsportsGame;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EsportsGameRepository extends JpaRepository<EsportsGame, Long> {

    Optional<EsportsGame> findByExternalGameId(String externalGameId);

    List<EsportsGame> findByMatchIdOrderByGameNumberAsc(Long matchId);

    /** 적재가 끝난 세트만 — 과거 조회는 이것만 DB 로 응답한다 */
    Optional<EsportsGame> findByExternalGameIdAndFinalizedAtIsNotNull(String externalGameId);
}
