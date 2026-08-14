package com.clutch.betting.repository;

import com.clutch.betting.domain.BettingEvent;
import com.clutch.betting.domain.BettingEventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

/** 배팅 이벤트 조회와 정산·동기화에 필요한 잠금 쿼리를 제공한다. */
public interface BettingEventRepository extends JpaRepository<BettingEvent, Long> {

    /** 외부 매치와 세트 번호로 이벤트를 조회한다. */
    Optional<BettingEvent> findByExternalMatchIdAndSetNumber(String externalMatchId, int setNumber);

    /** 실제 외부 게임 ID에 연결된 이벤트를 조회한다. */
    Optional<BettingEvent> findByExternalGameId(String externalGameId);

    /** 특정 생명주기 상태의 이벤트를 모두 조회한다. */
    List<BettingEvent> findAllByStatus(BettingEventStatus status);

    /** 매치에서 현재 상태 목록에 포함되는 가장 최근 세트 이벤트를 조회한다. */
    Optional<BettingEvent> findFirstByExternalMatchIdAndStatusInOrderBySetNumberDesc(
            String externalMatchId,
            List<BettingEventStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from BettingEvent event where event.id = :id")
    /** 배팅 등록·정산과 동기화의 상태 충돌을 막기 위해 이벤트 행을 잠가 조회한다. */
    Optional<BettingEvent> findByIdForUpdate(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event
            from BettingEvent event
            where event.externalMatchId = :externalMatchId
              and event.setNumber = :setNumber
            """)
    /** 동기화 대상 매치·세트 이벤트를 쓰기 잠금으로 조회한다. */
    Optional<BettingEvent> findByExternalMatchIdAndSetNumberForUpdate(
            String externalMatchId,
            int setNumber
    );

    @Query("""
            select event.id
            from BettingEvent event
            where event.status = com.clutch.betting.domain.BettingEventStatus.CLOSED
              and event.winnerExternalTeamId is not null
            """)
    /** 승자가 확인되어 정산 가능한 종료 이벤트 ID를 조회한다. */
    List<Long> findIdsReadyToSettle();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event
            from BettingEvent event
            where event.externalMatchId = :externalMatchId
              and event.setNumber > :setNumber
            order by event.setNumber
            """)
    /** 경기 종료 시 취소할 후속 세트 이벤트를 순서대로 잠가 조회한다. */
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
    /** 등록 상태 배팅이 남아 있어 환불이 필요한 취소 이벤트 ID를 조회한다. */
    List<Long> findIdsCancelledWithPlacedBets();
}
