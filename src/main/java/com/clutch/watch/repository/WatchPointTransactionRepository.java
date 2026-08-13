package com.clutch.watch.repository;

import com.clutch.watch.domain.WatchPointTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 시청 포인트 거래 저장소.
 */
public interface WatchPointTransactionRepository
        extends JpaRepository<WatchPointTransaction, Long> {

    boolean existsByWatchSessionId(Long watchSessionId);

    Optional<WatchPointTransaction> findByWatchSessionId(Long watchSessionId);
}
