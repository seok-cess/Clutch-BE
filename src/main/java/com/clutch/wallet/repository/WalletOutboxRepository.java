package com.clutch.wallet.repository;

import com.clutch.wallet.domain.WalletOutbox;
import com.clutch.wallet.domain.WalletOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 지갑 아웃박스 레코드의 저장과 발행 대상 조회를 담당하는 저장소.
 */
public interface WalletOutboxRepository extends JpaRepository<WalletOutbox, Long> {

    /** 발행되지 않은 아웃박스 레코드를 ID 오름차순으로 최대 100건 조회한다. */
    List<WalletOutbox> findTop100ByStatusOrderByIdAsc(WalletOutboxStatus status);
}
