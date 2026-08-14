package com.clutch.betting.repository;

import com.clutch.betting.domain.BetPointTransaction;
import com.clutch.betting.domain.BetPointTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BetPointTransactionRepository extends JpaRepository<BetPointTransaction, Long> {

    boolean existsByUserBetIdAndTransactionType(
            Long userBetId,
            BetPointTransactionType transactionType
    );

    Optional<BetPointTransaction> findByUserBetIdAndTransactionType(
            Long userBetId,
            BetPointTransactionType transactionType
    );
}
