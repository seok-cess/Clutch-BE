package com.clutch.coupon.admin.dashboard.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 페이지 운영 홈에서 사용하는 쿠폰 대시보드 전체 응답이다.
 *
 * @param generatedAt 대시보드 생성 시각(Asia/Seoul)
 * @param summary 운영 기준일의 쿠폰 발급 요약
 * @param issuanceTrend 운영 기준일까지의 일별 발급 추이
 * @param alerts 관리자 확인이 필요한 파생 알림
 * @param events 운영 홈에 노출할 쿠폰 이벤트 목록
 */
public record AdminCouponDashboardResponse(
        LocalDateTime generatedAt,
        CouponDashboardSummaryResponse summary,
        List<DailyIssuanceResponse> issuanceTrend,
        List<DashboardAlertResponse> alerts,
        List<AdminDashboardEventResponse> events
) {
}
