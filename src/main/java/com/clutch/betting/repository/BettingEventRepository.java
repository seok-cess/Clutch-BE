package com.clutch.betting.repository;

import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.BettingEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BettingEventRepository extends JpaRepository<BettingEvent, Long> {

    Optional<BettingEvent> findByExternalMatchIdAndSetNumber(String externalMatchId, int setNumber);

    Optional<BettingEvent> findByExternalGameId(String externalGameId);

    List<BettingEvent> findAllByStatus(BettingEventStatus status);
}
