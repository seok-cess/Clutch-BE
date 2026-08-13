package com.clutch.coupon.event.api.dto;

import com.clutch.coupon.event.domain.CouponEventOpenMode;
import com.clutch.coupon.event.domain.CouponEventStatus;
import com.clutch.coupon.event.domain.CouponIssueMode;

import java.time.LocalDateTime;

public record CouponEventSummaryResponse(
        Long couponEventId,
        String eventName,
        Long esportsMatchId,
        String triggerType,
        CouponEventOpenMode openMode,
        CouponIssueMode issueMode,
        CouponEventStatus eventStatus,
        int claimWindowSeconds,
        LocalDateTime scheduledOpenAt,
        long totalQuantity,
        long issuedQuantity,
        long remainingQuantity,
        LocalDateTime createdAt
) {
}
