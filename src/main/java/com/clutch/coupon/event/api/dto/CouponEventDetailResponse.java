package com.clutch.coupon.event.api.dto;

import com.clutch.coupon.event.domain.CouponEventOpenMode;
import com.clutch.coupon.event.domain.CouponEventStatus;
import com.clutch.coupon.event.domain.CouponIssueMode;

import java.time.LocalDateTime;
import java.util.List;

public record CouponEventDetailResponse(
        Long couponEventId,
        Long esportsMatchId,
        String eventName,
        CouponEventOpenMode openMode,
        CouponIssueMode issueMode,
        String triggerType,
        CouponEventStatus eventStatus,
        int claimWindowSeconds,
        LocalDateTime scheduledOpenAt,
        long totalQuantity,
        long issuedQuantity,
        long remainingQuantity,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<CouponEventItemDetailResponse> items,
        CouponEventOccurrenceResponse latestOccurrence
) {
}
