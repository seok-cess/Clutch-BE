package com.clutch.coupon.statistics.repository;

import com.clutch.coupon.event.domain.CouponEventStatus;

import java.time.LocalDateTime;

/** 관리자 대시보드에 표시할 쿠폰 이벤트별 발급 통계 조회 결과. */
public record CouponIssueStatisticsEventRow(
        Long couponEventId,
        String eventName,
        String triggerType,
        CouponEventStatus eventStatus,
        long successCount,
        long failureCount,
        long processingErrorCount,
        LocalDateTime lastResultAt,
        LocalDateTime lastErrorAt
) {
}
