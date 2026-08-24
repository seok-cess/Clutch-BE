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

    /**
     * 외부 매치와 세트 번호로 이벤트를 조회한다.
     *
     * @param externalMatchId 외부 매치 ID
     * @param setNumber 세트 번호
     * @return 조건에 맞는 배팅 이벤트
     */
    Optional<BettingEvent> findByExternalMatchIdAndSetNumber(String externalMatchId, int setNumber);

    /**
     * 실제 외부 게임 ID에 연결된 이벤트를 조회한다.
     *
     * @param externalGameId 외부 게임 ID
     * @return 외부 게임에 연결된 배팅 이벤트
     */
    Optional<BettingEvent> findByExternalGameId(String externalGameId);

    /**
     * 특정 생명주기 상태의 이벤트를 모두 조회한다.
     *
     * @param status 조회할 배팅 이벤트 상태
     * @return 해당 상태의 배팅 이벤트 목록
     */
    List<BettingEvent> findAllByStatus(BettingEventStatus status);

    /** 한 매치의 기존 결과를 복원하고 미확정 세트를 찾기 위한 전체 이벤트 조회. */
    List<BettingEvent> findAllByExternalMatchId(String externalMatchId);

    /**
     * 상태와 무관하게 매치의 가장 최근 세트 이벤트를 조회한다.
     *
     * @param externalMatchId 외부 매치 ID
     * @return 가장 큰 세트 번호를 가진 이벤트
     */
    Optional<BettingEvent> findFirstByExternalMatchIdOrderBySetNumberDesc(String externalMatchId);

    /**
     * 배팅 등록·정산과 동기화의 상태 충돌을 막기 위해 이벤트 행을 잠가 조회한다.
     *
     * @param id 배팅 이벤트 ID
     * @return 쓰기 잠금이 적용된 배팅 이벤트
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from BettingEvent event where event.id = :id")
    Optional<BettingEvent> findByIdForUpdate(Long id);

    /**
     * 동기화 대상 매치·세트 이벤트를 쓰기 잠금으로 조회한다.
     *
     * @param externalMatchId 외부 매치 ID
     * @param setNumber 세트 번호
     * @return 쓰기 잠금이 적용된 배팅 이벤트
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event
            from BettingEvent event
            where event.externalMatchId = :externalMatchId
              and event.setNumber = :setNumber
            """)
    Optional<BettingEvent> findByExternalMatchIdAndSetNumberForUpdate(
            String externalMatchId,
            int setNumber
    );

    /** 결과 재조회로 확인된 승자를 반영할 미정산 종료 이벤트를 잠가 조회한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event
            from BettingEvent event
            where event.externalMatchId = :externalMatchId
              and event.status = com.clutch.betting.domain.BettingEventStatus.CLOSED
              and event.winnerExternalTeamId is null
            """)
    List<BettingEvent> findAllClosedWithoutWinnerForUpdate(String externalMatchId);

    /**
     * livestats 종료가 DB에 적재된 세트에 연결된 미정산 이벤트를 잠가 조회한다.
     * 결과 재조회 전에 아직 열린 이벤트를 닫아, 라이브 캐시 정리와 동시에 정산 대상이
     * 누락되는 경우를 막는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select event
            from BettingEvent event, com.clutch.lolesports.entity.EsportsGame game
            where event.externalMatchId = :externalMatchId
              and event.externalGameId = game.externalGameId
              and event.status in (
                    com.clutch.betting.domain.BettingEventStatus.OPEN,
                    com.clutch.betting.domain.BettingEventStatus.CLOSED
              )
              and event.winnerExternalTeamId is null
              and game.endedAt is not null
            """)
    List<BettingEvent> findAllUnsettledFinishedGameEventsForUpdate(String externalMatchId);

    /**
     * 승자가 확인되어 정산 가능한 종료 이벤트 ID를 조회한다.
     *
     * @return 정산 가능한 배팅 이벤트 ID 목록
     */
    @Query("""
            select event.id
            from BettingEvent event
            where event.status = com.clutch.betting.domain.BettingEventStatus.CLOSED
              and event.winnerExternalTeamId is not null
            """)
    List<Long> findIdsReadyToSettle();

    /**
     * 경기 종료 시 취소할 후속 세트 이벤트를 순서대로 잠가 조회한다.
     *
     * @param externalMatchId 외부 매치 ID
     * @param setNumber 마지막으로 종료된 세트 번호
     * @return 쓰기 잠금이 적용된 후속 이벤트 목록
     */
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

    /**
     * 실제 게임 ID 없이 선개설된 다음 세트 이벤트가 있는 매치를 찾는다.
     *
     * <p>마지막 세트 직후 공식 match 완료 응답이 늦게 오는 경우에도 결과 조정 작업이
     * 매치 상세를 계속 재조회해 잘못 열린 후속 이벤트를 취소·환불할 수 있게 한다.</p>
     *
     * @return 미연결 후속 세트 이벤트를 가진 매치 외부 ID 목록
     */
    @Query("""
            select distinct event.externalMatchId
            from BettingEvent event
            where event.status = com.clutch.betting.domain.BettingEventStatus.OPEN
              and event.setNumber > 1
              and event.externalGameId is null
            """)
    List<String> findExternalMatchIdsWithOpenSpeculativeFutureEvent();

    /**
     * 게임 적재 여부와 무관하게 종료됐지만 승자가 없는 이벤트의 매치를 찾는다.
     *
     * <p>고배속 재생이나 일시적인 라이브 폴링 누락으로 {@code esports_game} 행이 아직
     * 만들어지지 않아도, 이미 닫힌 배팅 이벤트는 공식 매치 상세 재조회 대상이어야 한다.</p>
     *
     * @return 승자 미확정 종료 이벤트를 가진 매치 외부 ID 목록
     */
    @Query("""
            select distinct event.externalMatchId
            from BettingEvent event
            where event.status = com.clutch.betting.domain.BettingEventStatus.CLOSED
              and event.externalGameId is not null
              and event.winnerExternalTeamId is null
            """)
    List<String> findExternalMatchIdsWithClosedEventWithoutWinner();

    /**
     * 등록 상태 배팅이 남아 있어 환불이 필요한 취소 이벤트 ID를 조회한다.
     *
     * @return 환불할 사용자 배팅이 남은 취소 이벤트 ID 목록
     */
    @Query("""
            select distinct event.id
            from BettingEvent event
            join UserBet bet on bet.bettingEventId = event.id
            where event.status = com.clutch.betting.domain.BettingEventStatus.CANCELLED
              and bet.status = com.clutch.betting.domain.UserBetStatus.PLACED
            """)
    List<Long> findIdsCancelledWithPlacedBets();
}
