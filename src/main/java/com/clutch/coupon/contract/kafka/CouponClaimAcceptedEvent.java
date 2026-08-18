package com.clutch.coupon.contract.kafka;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 쿠폰 발급 접수 이벤트
 */
public record CouponClaimAcceptedEvent(
        int eventVersion,
        String messageId,
        Long claimId,
        Long userId,
        Long couponEventId,
        Long couponEventOccurrenceId,
        Long couponEventItemId,
        String discountType,
        BigDecimal discountValue,
        Instant expiresAt,
        Instant occurredAt
) {
}