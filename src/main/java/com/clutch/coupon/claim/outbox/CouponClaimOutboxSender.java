package com.clutch.coupon.claim.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

/**
 * 쿠폰 발급 Outbox Kafka 전송기
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponClaimOutboxSender {

    private static final long SEND_TIMEOUT_SECONDS = 5;

    private final CouponClaimOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Outbox Kafka 전송
     *
     * @param outboxId Outbox 식별자
     */
    @Transactional
    public void send(
            Long outboxId
    ) {
        CouponClaimOutbox outbox =
                outboxRepository
                        .findById(outboxId)
                        .orElse(null);

        if (outbox == null
                || outbox.getStatus()
                != CouponClaimOutboxStatus.PENDING) {
            return;
        }

        try {
            kafkaTemplate.send(
                            outbox.getTopic(),
                            String.valueOf(
                                    outbox.getAggregateId()
                            ),
                            outbox.getPayload()
                    )
                    .get(
                            SEND_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    );

            outbox.markSent(
                    LocalDateTime.now(ZoneOffset.UTC)
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            outbox.increaseRetryCount();

            log.warn(
                    "쿠폰 발급 Outbox 전송 중단: outboxId={}",
                    outboxId,
                    exception
            );
        } catch (Exception exception) {
            outbox.increaseRetryCount();

            log.warn(
                    "쿠폰 발급 Outbox 전송 실패: outboxId={}",
                    outboxId,
                    exception
            );
        }
    }
}