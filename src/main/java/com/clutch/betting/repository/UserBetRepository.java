package com.clutch.betting.repository;

import com.clutch.betting.domain.UserBet;
import com.clutch.betting.domain.UserBetStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface UserBetRepository extends JpaRepository<UserBet, Long> {

    Optional<UserBet> findByBettingEventIdAndUserId(Long bettingEventId, Long userId);

    boolean existsByBettingEventIdAndUserId(Long bettingEventId, Long userId);

    List<UserBet> findAllByBettingEventIdAndStatus(Long bettingEventId, UserBetStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select bet
            from UserBet bet
            where bet.bettingEventId = :bettingEventId
              and bet.status = :status
            order by bet.userId, bet.id
            """)
    List<UserBet> findAllByBettingEventIdAndStatusForUpdate(
            Long bettingEventId,
            UserBetStatus status
    );
}
