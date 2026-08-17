package com.clutch.wallet.repository;

import com.clutch.wallet.domain.WalletOutbox;
import com.clutch.wallet.domain.WalletOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WalletOutboxRepository extends JpaRepository<WalletOutbox, Long> {
    List<WalletOutbox> findTop100ByStatusOrderByIdAsc(WalletOutboxStatus status);
}
