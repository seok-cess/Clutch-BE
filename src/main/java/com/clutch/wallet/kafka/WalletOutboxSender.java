package com.clutch.wallet.kafka;

import com.clutch.wallet.domain.WalletOutbox;
import com.clutch.wallet.domain.WalletOutboxStatus;
import com.clutch.wallet.repository.WalletOutboxRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class WalletOutboxSender {

    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final WalletOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public WalletOutboxSender(WalletOutboxRepository outboxRepository,
                              KafkaTemplate<String, String> kafkaTemplate){
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public void send(Long outboxId){
        WalletOutbox outbox = outboxRepository.findById(outboxId).orElse(null);
        if(outbox == null || outbox.getStatus() != WalletOutboxStatus.PENDING){
            return;
        }
        try{
            kafkaTemplate.send(outbox.getTopic(),
                            String.valueOf(outbox.getAggregateId()), outbox.getPayload())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            outbox.markSent(Instant.now());
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
            outbox.increaseRetryCount();
        }catch(Exception e){
            outbox.increaseRetryCount();
        }
    }
}
