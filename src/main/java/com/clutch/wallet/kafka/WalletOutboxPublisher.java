package com.clutch.wallet.kafka;

import com.clutch.wallet.domain.WalletOutbox;
import com.clutch.wallet.domain.WalletOutboxStatus;
import com.clutch.wallet.repository.WalletOutboxRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "wallet.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WalletOutboxPublisher {

    private final WalletOutboxRepository outboxRepository;
    private final WalletOutboxSender outboxSender;

    public WalletOutboxPublisher(WalletOutboxRepository outboxRepository,
                                 WalletOutboxSender outboxSender){
        this.outboxRepository = outboxRepository;
        this.outboxSender = outboxSender;
    }

    @Scheduled(fixedDelayString = "${wallet.outbox.publish-interval-ms:1000}")
    public void publishPending(){
        List<WalletOutbox> pending = outboxRepository.findTop100ByStatusOrderByIdAsc(WalletOutboxStatus.PENDING);
        for(WalletOutbox outbox : pending){
            outboxSender.send(outbox.getId());
        }
    }
}
