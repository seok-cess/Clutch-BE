package com.clutch.coupon.statistics.repository;

import java.time.LocalDateTime;

/** 관리자 쿠폰 발급 통계 전체 집계 조회 결과. */
public record CouponIssueStatisticsSummaryRow(
        long successCount,
        long failureCount,
        long processingErrorCount,
        long unassignedErrorCount,
        LocalDateTime lastProcessedAt
) {
}
