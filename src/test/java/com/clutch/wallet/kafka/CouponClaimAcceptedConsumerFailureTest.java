package com.clutch.wallet.kafka;

import com.clutch.coupon.contract.kafka.CouponClaimAcceptedEvent;
import com.clutch.wallet.service.CouponIssuanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponClaimAcceptedConsumerFailureTest {
    @Mock private CouponIssuanceService couponIssuanceService;
    @Mock private ObjectMapper objectMapper;

    @Test
    void issue가_실패하면_recordIssueFailure를_claimId와_사유로_호출한다() throws Exception {
        CouponClaimAcceptedConsumer consumer = new CouponClaimAcceptedConsumer(couponIssuanceService, objectMapper);
        CouponClaimAcceptedEvent event = new CouponClaimAcceptedEvent(
                1, "msg-1", 1001L, 1L, 10L, null, 100L,
                "RATE", new BigDecimal("50.00"), Instant.now(), Instant.now());
        when(objectMapper.readValue(anyString(), eq(CouponClaimAcceptedEvent.class))).thenReturn(event);
        doThrow(new IllegalStateException("DB 연결 끊김")).when(couponIssuanceService).issue(event);

        consumer.onClaimAccepted("{...}");

        verify(couponIssuanceService).recordIssueFailure(1001L, "DB 연결 끊김");
    }
}