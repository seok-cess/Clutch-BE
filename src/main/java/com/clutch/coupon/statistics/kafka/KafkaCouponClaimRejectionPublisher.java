package com.clutch.coupon.statistics.kafka;

import com.clutch.coupon.claim.exception.CouponClaimErrorCode;
import com.clutch.coupon.claim.service.CouponClaimRejectionPublisher;
import com.clutch.coupon.contract.kafka.CouponClaimRejectedEvent;
import com.clutch.coupon.contract.kafka.CouponKafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.UUID;

/** 쿠폰 신청 거절을 사용자 응답을 기다리지 않는 Kafka 이벤트로 발행한다. */
@Slf4j
@Component
public class KafkaCouponClaimRejectionPublisher
        implements CouponClaimRejectionPublisher {

    private static final int EVENT_VERSION = 1;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String topic;

    public KafkaCouponClaimRejectionPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${coupon.claim.kafka.rejected-topic:"
                    + CouponKafkaTopics.CLAIM_REJECTED
                    + "}")
            String topic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.topic = topic;
    }

    @Override
    @Async("couponClaimRejectionExecutor")
    public void publish(
            Long couponEventId,
            Long couponEventOccurrenceId,
            CouponClaimErrorCode errorCode
    ) {
        CouponClaimRejectedEvent event = new CouponClaimRejectedEvent(
                EVENT_VERSION,
                UUID.randomUUID().toString(),
                couponEventId,
                couponEventOccurrenceId,
                errorCode.name(),
                clock.instant()
        );

        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(
                    topic,
                    String.valueOf(couponEventId),
                    payload
            ).whenComplete((result, exception) -> {
                if (exception != null) {
                    log.warn(
                            "쿠폰 신청 거절 Kafka 발행 실패: eventId={}, reason={}",
                            couponEventId,
                            errorCode,
                            exception
                    );
                }
            });
        } catch (Exception exception) {
            log.warn(
                    "쿠폰 신청 거절 이벤트 직렬화 실패: eventId={}, reason={}",
                    couponEventId,
                    errorCode,
                    exception
            );
        }
    }
}
