package com.clutch.coupon.claim.outbox;

import com.clutch.coupon.claim.domain.CouponClaimRequest;
import com.clutch.coupon.contract.kafka.CouponClaimAcceptedEvent;
import com.clutch.coupon.contract.kafka.CouponKafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 쿠폰 발급 Outbox 작성기
 */
@Component
@RequiredArgsConstructor
public class CouponClaimOutboxWriter {

    private static final int EVENT_VERSION = 1;
    private static final int COUPON_VALID_DAYS = 7;

    private final CouponClaimOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * 쿠폰 발급 접수 이벤트 저장
     *
     * @param claimRequest 쿠폰 발급 요청
     * @param benefitSnapshot 쿠폰 혜택 스냅샷
     * @param occurredAt 이벤트 발생 시각
     */
    public void writeAcceptedEvent(
            CouponClaimRequest claimRequest,
            CouponBenefitSnapshot benefitSnapshot,
            Instant occurredAt
    ) {
        String messageId = UUID.randomUUID().toString();

        CouponClaimAcceptedEvent event =
                new CouponClaimAcceptedEvent(
                        EVENT_VERSION,
                        messageId,
                        claimRequest.getId(),
                        claimRequest.getUserId(),
                        claimRequest.getCouponEventId(),
                        claimRequest.getCouponEventOccurrenceId(),
                        claimRequest.getCouponEventItemId(),
                        benefitSnapshot.discountType(),
                        benefitSnapshot.discountValue(),
                        occurredAt.plus(
                                COUPON_VALID_DAYS,
                                ChronoUnit.DAYS
                        ),
                        occurredAt
                );

        String payload = serialize(event);

        CouponClaimOutbox outbox =
                CouponClaimOutbox.create(
                        messageId,
                        claimRequest.getId(),
                        CouponKafkaTopics.CLAIM_ACCEPTED,
                        payload
                );

        outboxRepository.save(outbox);
    }

    /**
     * 쿠폰 발급 접수 이벤트 직렬화
     *
     * @param event 쿠폰 발급 접수 이벤트
     * @return JSON 페이로드
     */
    private String serialize(
            CouponClaimAcceptedEvent event
    ) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "쿠폰 발급 이벤트 직렬화 실패",
                    exception
            );
        }
    }
}