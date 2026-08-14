package com.clutch.betting.repository;

import com.clutch.betting.domain.UserBet;
import com.clutch.betting.domain.UserBetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

/** 사용자 배팅 조회와 정산 시 행 잠금을 제공한다. */
public interface UserBetRepository extends JpaRepository<UserBet, Long> {

    /** 이벤트에 등록한 특정 사용자의 배팅을 조회한다. */
    Optional<UserBet> findByBettingEventIdAndUserId(Long bettingEventId, Long userId);

    /** 동일 이벤트에 사용자가 이미 배팅했는지 확인한다. */
    boolean existsByBettingEventIdAndUserId(Long bettingEventId, Long userId);

    /** 이벤트와 처리 상태에 해당하는 사용자 배팅을 조회한다. */
    List<UserBet> findAllByBettingEventIdAndStatus(Long bettingEventId, UserBetStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select bet
            from UserBet bet
            where bet.bettingEventId = :bettingEventId
              and bet.status = :status
            order by bet.userId, bet.id
            """)
    /** 정산·환불 교착을 줄이도록 사용자와 ID 순서로 배팅 행을 잠가 조회한다. */
    List<UserBet> findAllByBettingEventIdAndStatusForUpdate(
            Long bettingEventId,
            UserBetStatus status
    );
}
