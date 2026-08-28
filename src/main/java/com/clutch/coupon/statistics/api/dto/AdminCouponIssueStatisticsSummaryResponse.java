package com.clutch.coupon.statistics.api.dto;

import java.time.LocalDateTime;

/** 관리자 대시보드의 쿠폰 발급 전체 통계. */
public record AdminCouponIssueStatisticsSummaryResponse(
        long totalResultCount,
        long successCount,
        long failureCount,
        long processingErrorCount,
        long unassignedErrorCount,
        LocalDateTime lastProcessedAt
) {
}
