package com.clutch.coupon.statistics.api.dto;

import java.util.List;

/** 관리자 쿠폰 발급 통계 대시보드 응답. */
public record AdminCouponIssueStatisticsResponse(
        AdminCouponIssueStatisticsSummaryResponse summary,
        List<AdminCouponIssueStatisticsEventResponse> events
) {
}
