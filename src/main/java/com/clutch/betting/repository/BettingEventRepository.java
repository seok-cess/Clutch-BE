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

    Optional<BettingEvent> findFirstByExternalMatchIdAndStatusInOrderBySetNumberDesc(
            String externalMatchId,
            List<BettingEventStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from BettingEvent event where event.id = :id")
    Optional<BettingEvent> findByIdForUpdate(Long id);

    @Query("""
            select event.id
            from BettingEvent event
            where event.status = com.clutch.betting.domain.BettingEventStatus.CLOSED
              and event.winnerExternalTeamId is not null
            """)
    List<Long> findIdsReadyToSettle();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event
            from BettingEvent event
            where event.externalMatchId = :externalMatchId
              and event.setNumber > :setNumber
            order by event.setNumber
            """)
    List<BettingEvent> findAllFutureEventsForUpdate(
            String externalMatchId,
            int setNumber
    );

    @Query("""
            select distinct event.id
            from BettingEvent event
            join UserBet bet on bet.bettingEventId = event.id
            where event.status = com.clutch.betting.domain.BettingEventStatus.CANCELLED
              and bet.status = com.clutch.betting.domain.UserBetStatus.PLACED
            """)
    List<Long> findIdsCancelledWithPlacedBets();
}
