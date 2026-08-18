package com.clutch.coupon.test.event.api.dto;

import com.clutch.coupon.event.domain.CouponEventOccurrenceStatus;

import java.time.LocalDateTime;

/** 관리자 수동 오픈과 사용자 테스트 화면에서 공유하는 응답. */
public record CouponEventActivationResponse(
        Long couponEventId,
        Long couponEventOccurrenceId,
        String eventName,
        LocalDateTime openedAt,
        LocalDateTime expiresAt,
        CouponEventOccurrenceStatus occurrenceStatus,
        long remainingQuantity,
        boolean claimable
) {
}
