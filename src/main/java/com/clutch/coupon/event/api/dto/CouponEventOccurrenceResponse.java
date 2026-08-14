package com.clutch.coupon.event.api.dto;

import com.clutch.coupon.event.domain.CouponEventOccurrenceStatus;

import java.time.LocalDateTime;

public record CouponEventOccurrenceResponse(
        Long couponEventOccurrenceId,
        Long matchEventId,
        String sourceEventKey,
        Integer gameTimeSeconds,
        LocalDateTime sourceOccurredAt,
        LocalDateTime detectedAt,
        LocalDateTime openedAt,
        LocalDateTime expiresAt,
        LocalDateTime closedAt,
        CouponEventOccurrenceStatus occurrenceStatus,
        String closeReason
) {
}
