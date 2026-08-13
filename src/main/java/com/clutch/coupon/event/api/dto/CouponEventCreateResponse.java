package com.clutch.coupon.event.api.dto;

import com.clutch.coupon.event.domain.CouponEventOpenMode;
import com.clutch.coupon.event.domain.CouponEventStatus;
import com.clutch.coupon.event.domain.CouponIssueMode;

import java.time.LocalDateTime;
import java.util.List;

public record CouponEventCreateResponse(
        Long couponEventId,
        Long esportsMatchId,
        String eventName,
        CouponEventOpenMode openMode,
        CouponIssueMode issueMode,
        String triggerType,
        CouponEventStatus eventStatus,
        int claimWindowSeconds,
        LocalDateTime scheduledOpenAt,
        LocalDateTime createdAt,
        List<CouponEventItemCreateResponse> items
) {
}
