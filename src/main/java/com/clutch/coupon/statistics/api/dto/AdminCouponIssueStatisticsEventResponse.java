package com.clutch.coupon.statistics.api.dto;

import com.clutch.coupon.event.domain.CouponEventStatus;

import java.time.LocalDateTime;

/** 관리자 대시보드의 쿠폰 이벤트별 발급 통계. */
public record AdminCouponIssueStatisticsEventResponse(
        Long couponEventId,
        String eventName,
        String triggerType,
        CouponEventStatus eventStatus,
        long totalResultCount,
        long successCount,
        long failureCount,
        long processingErrorCount,
        LocalDateTime lastResultAt,
        LocalDateTime lastErrorAt
) {
}
