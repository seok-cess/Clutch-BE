package com.clutch.coupon.admin.dashboard.repository;

/**
 * 관리자 페이지 운영 홈의 당일 쿠폰 발급 집계 조회 결과다.
 *
 * @param requestCount 전체 요청 수
 * @param issuedCount 발급 성공 수
 * @param failedCount 발급 실패 수
 * @param pendingCount 처리 대기 수
 */
public record CouponDashboardAggregateRow(
        long requestCount,
        long issuedCount,
        long failedCount,
        long pendingCount
) {
}
