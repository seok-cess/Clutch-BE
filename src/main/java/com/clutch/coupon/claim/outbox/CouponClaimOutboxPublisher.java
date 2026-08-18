package com.clutch.coupon.claim.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.List;

/**
 * 쿠폰 발급 Outbox 발행 스케줄러
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "coupon.claim.outbox",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)

public class CouponClaimOutboxPublisher {

    private final CouponClaimOutboxRepository outboxRepository;
    private final CouponClaimOutboxSender outboxSender;

    /**
     * 발행 대기 Outbox 처리
     */
    @Scheduled(
            fixedDelayString =
                    "${coupon.claim.outbox.publish-interval-ms:1000}"
    )
    public void publishPending() {
        List<CouponClaimOutbox> pendingOutboxes =
                outboxRepository
                        .findTop100ByStatusOrderByIdAsc(
                                CouponClaimOutboxStatus.PENDING
                        );

        for (CouponClaimOutbox outbox : pendingOutboxes) {
            try {
                outboxSender.send(outbox.getId());
            } catch (Exception exception) {
                log.error(
                        "쿠폰 발급 Outbox 처리 오류: outboxId={}",
                        outbox.getId(),
                        exception
                );
            }
        }
    }
}