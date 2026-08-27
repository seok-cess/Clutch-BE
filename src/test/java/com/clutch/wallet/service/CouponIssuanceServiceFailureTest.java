package com.clutch.wallet.service;

import com.clutch.coupon.contract.kafka.CouponIssueResultEvent;
import com.clutch.coupon.contract.kafka.CouponIssueResultStatus;
import com.clutch.wallet.domain.WalletOutbox;
import com.clutch.wallet.repository.WalletOutboxRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class CouponIssuanceServiceFailureTest {
    @Autowired private CouponIssuanceService couponIssuanceService;
    @Autowired private WalletOutboxRepository walletOutboxRepository;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void recordIssueFailure는_FAILED_상태의_outbox를_남긴다() throws Exception {
        Long claimId = UUID.randomUUID().getMostSignificantBits()
                & Long.MAX_VALUE;
        couponIssuanceService.recordIssueFailure(claimId, "테스트 실패 사유");

        WalletOutbox outbox = walletOutboxRepository.findAll().stream()
                .filter(o -> o.getAggregateId().equals(claimId))
                .findFirst().orElseThrow();

        CouponIssueResultEvent event = objectMapper.readValue(outbox.getPayload(), CouponIssueResultEvent.class);
        assertEquals(claimId, event.claimId());
        assertNull(event.couponId());
        assertEquals(CouponIssueResultStatus.FAILED, event.status());
        assertEquals("테스트 실패 사유", event.failureReason());
    }
}
