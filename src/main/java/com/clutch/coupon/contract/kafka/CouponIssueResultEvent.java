package com.clutch.coupon.contract.kafka;

import java.time.Instant;

/**
 * 쿠폰 생성 결과 이벤트
 */
public record CouponIssueResultEvent(
        int eventVersion,
        String messageId,
        Long claimId,
        Long couponId,
        CouponIssueResultStatus status,
        String failureReason,
        Instant occurredAt
) {
}