package com.clutch.wallet.domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserCouponTest {

    private static final Instant REFERENCE_TIME =
            Instant.parse("2026-08-21T12:00:00Z");

    @Test
    void ISSUED는_만료시각부터_EXPIRED로_해석한다() {
        UserCoupon coupon = couponExpiringAt(REFERENCE_TIME);

        assertEquals(
                UserCouponStatus.EXPIRED,
                coupon.getEffectiveStatus(REFERENCE_TIME)
        );
    }

    @Test
    void 사용과_취소_상태는_만료시각이_지나도_유지한다() {
        UserCoupon used = couponExpiringAt(REFERENCE_TIME.minusSeconds(1));
        UserCoupon cancelled =
                couponExpiringAt(REFERENCE_TIME.minusSeconds(1));
        ReflectionTestUtils.setField(used, "status", UserCouponStatus.USED);
        ReflectionTestUtils.setField(
                cancelled,
                "status",
                UserCouponStatus.CANCELLED
        );

        assertEquals(
                UserCouponStatus.USED,
                used.getEffectiveStatus(REFERENCE_TIME)
        );
        assertEquals(
                UserCouponStatus.CANCELLED,
                cancelled.getEffectiveStatus(REFERENCE_TIME)
        );
    }

    private UserCoupon couponExpiringAt(Instant expiresAt) {
        return new UserCoupon(
                1L,
                1L,
                10L,
                null,
                100L,
                "CPN-STATUS-POLICY",
                "RATE",
                new BigDecimal("50.00"),
                expiresAt
        );
    }
}
