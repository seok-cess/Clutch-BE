package com.clutch.coupon.statistics.kafka;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import static com.clutch.coupon.claim.exception.CouponClaimErrorCode.COUPON_ALREADY_CLAIMED;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaCouponClaimRejectionPublisherTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaCouponClaimRejectionPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new KafkaCouponClaimRejectionPublisher(
                kafkaTemplate,
                new ObjectMapper(),
                Clock.fixed(
                        Instant.parse("2026-08-29T05:00:00Z"),
                        ZoneOffset.UTC
                ),
                "coupon.claim.rejected.test"
        );
    }

    @Test
    void 거절_이벤트를_사용자_응답을_기다리지_않고_발행한다() {
        when(kafkaTemplate.send(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(CompletableFuture.completedFuture(null));

        publisher.publish(10L, 20L, COUPON_ALREADY_CLAIMED);

        verify(kafkaTemplate).send(
                eq("coupon.claim.rejected.test"),
                eq("10"),
                argThat(payload -> payload.contains("COUPON_ALREADY_CLAIMED")
                        && payload.contains("2026-08-29T05:00:00Z"))
        );
    }
}
