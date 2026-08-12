package com.clutch.lolesports.repository;

import com.clutch.lolesports.entity.EsportsMatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EsportsMatchRepository extends JpaRepository<EsportsMatch, Long> {

    Optional<EsportsMatch> findByExternalMatchId(String externalMatchId);

    boolean existsByExternalMatchId(String externalMatchId);

    /** 일정 화면 — 기간으로 잘라 조회한다 (전체를 한 번에 내리지 않기 위해) */
    List<EsportsMatch> findByScheduledAtBetweenOrderByScheduledAtAsc(
            LocalDateTime from, LocalDateTime to);

    List<EsportsMatch> findByLifecycleStatusOrderByScheduledAtDesc(String lifecycleStatus);
}
