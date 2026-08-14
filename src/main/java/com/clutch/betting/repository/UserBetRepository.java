package com.clutch.betting.repository;

import com.clutch.betting.domain.UserBet;
import com.clutch.betting.domain.UserBetStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserBetRepository extends JpaRepository<UserBet, Long> {

    Optional<UserBet> findByBettingEventIdAndUserId(Long bettingEventId, Long userId);

    boolean existsByBettingEventIdAndUserId(Long bettingEventId, Long userId);

    List<UserBet> findAllByBettingEventIdAndStatus(Long bettingEventId, UserBetStatus status);
}
