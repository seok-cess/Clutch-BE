package com.clutch.watch.repository;

import com.clutch.watch.domain.WatchPointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 시청 포인트 거래 저장소.
 */
public interface WatchPointTransactionRepository
        extends JpaRepository<WatchPointTransaction, Long> {

    boolean existsByWatchSessionIdAndRewardSequence(Long watchSessionId, long rewardSequence);

    Optional<WatchPointTransaction> findByWatchSessionIdAndRewardSequence(
            Long watchSessionId,
            long rewardSequence
    );

    /** 특정 사용자가 한 번에 수령한 최대 시청 보상 포인트를 조회한다. */
    @Query("""
            select coalesce(max(pointTransaction.awardedPoint), 0)
            from WatchPointTransaction pointTransaction
            where pointTransaction.userId = :userId
            """)
    long findMaxAwardedPointByUserId(@Param("userId") Long userId);

    /** 특정 사용자가 수령한 시청 포인트를 최근 수령 순으로 조회한다. */
    List<WatchPointTransaction> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    @Transactional
    void deleteAllByWatchSessionId(Long watchSessionId);
}
