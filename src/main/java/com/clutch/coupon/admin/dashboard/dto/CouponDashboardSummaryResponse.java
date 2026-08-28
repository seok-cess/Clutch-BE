package com.clutch.coupon.admin.dashboard.dto;

import java.math.BigDecimal;

/**
 * 관리자 페이지 운영 홈에서 사용하는 쿠폰 발급 요약이다.
 *
 * @param openEventCount 전체 진행 중 이벤트 수
 * @param soldOutEventCount Redis 잔여 재고가 모두 소진된 진행 중 이벤트 수
 * @param todayRequestCount 운영 기준일에 생성된 전체 발급 요청 수
 * @param todayIssuedCount 운영 기준일 발급 성공 수
 * @param todayFailedCount 운영 기준일 발급 실패 수
 * @param todayPendingCount 운영 기준일 처리 대기 수
 * @param todaySuccessRate 성공과 실패만 분모로 계산한 성공률
 */
public record CouponDashboardSummaryResponse(
        long openEventCount,
        long soldOutEventCount,
        long todayRequestCount,
        long todayIssuedCount,
        long todayFailedCount,
        long todayPendingCount,
        BigDecimal todaySuccessRate
) {
}
