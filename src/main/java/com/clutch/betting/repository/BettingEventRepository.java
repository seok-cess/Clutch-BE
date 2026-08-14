package com.clutch.betting.repository;

import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.BettingEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface BettingEventRepository extends JpaRepository<BettingEvent, Long> {

    Optional<BettingEvent> findByExternalMatchIdAndSetNumber(String externalMatchId, int setNumber);

    Optional<BettingEvent> findByExternalGameId(String externalGameId);

    List<BettingEvent> findAllByStatus(BettingEventStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from BettingEvent event where event.id = :id")
    Optional<BettingEvent> findByIdForUpdate(Long id);
}
