package com.clutch.coupon.event.api.dto;

import com.clutch.coupon.event.domain.CouponEventStatus;
import com.clutch.coupon.event.domain.CouponIssueMode;

import java.time.LocalDateTime;
import java.util.List;

public record CouponEventUpdateResponse(
        Long couponEventId,
        Long esportsMatchId,
        String eventName,
        CouponIssueMode issueMode,
        String triggerType,
        CouponEventStatus eventStatus,
        int claimWindowSeconds,
        LocalDateTime updatedAt,
        List<CouponEventItemCreateResponse> items
) {
}
