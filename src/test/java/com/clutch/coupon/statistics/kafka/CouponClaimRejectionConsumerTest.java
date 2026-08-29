package com.clutch.coupon.statistics.kafka;

import com.clutch.coupon.contract.kafka.CouponClaimRejectedEvent;
import com.clutch.coupon.statistics.service.CouponClaimRejectionStatisticsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponClaimRejectionConsumerTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CouponClaimRejectionStatisticsService statisticsService;

    @Test
    void Kafka_거절_이벤트를_통계_서비스로_전달한다() throws Exception {
        CouponClaimRejectedEvent event = new CouponClaimRejectedEvent(
                1,
                "message-1",
                10L,
                20L,
                "COUPON_STOCK_EXHAUSTED",
                Instant.parse("2026-08-29T05:00:00Z")
        );
        when(objectMapper.readValue(
                "payload",
                CouponClaimRejectedEvent.class
        )).thenReturn(event);

        new CouponClaimRejectionConsumer(
                objectMapper,
                statisticsService
        ).consume("payload");

        verify(statisticsService).record(event);
    }
}
