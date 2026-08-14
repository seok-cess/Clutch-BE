package com.clutch.watch.repository;

import com.clutch.watch.domain.WatchPointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    void deleteAllByWatchSessionId(Long watchSessionId);
}
