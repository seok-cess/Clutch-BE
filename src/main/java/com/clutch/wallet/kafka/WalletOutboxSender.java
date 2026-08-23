package com.clutch.wallet.kafka;

import com.clutch.wallet.domain.WalletOutbox;
import com.clutch.wallet.domain.WalletOutboxStatus;
import com.clutch.wallet.repository.WalletOutboxRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * 아웃박스 레코드를 Kafka로 발행하고 발행 결과에 따라 상태를 갱신한다.
 */
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

    /**
     * 아웃박스 레코드를 Kafka로 발행한다.
     *
     * <p>이미 발행되었거나 존재하지 않는 레코드는 무시하며,
     * 발행 실패 또는 인터럽트 시 재시도 횟수를 증가시킨다.</p>
     *
     * @param outboxId 발행할 아웃박스 레코드 ID
     */
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
