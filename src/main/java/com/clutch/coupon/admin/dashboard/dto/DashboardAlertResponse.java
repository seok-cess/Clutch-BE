package com.clutch.coupon.admin.dashboard.dto;

/**
 * 관리자 페이지 운영 홈의 처리 필요 영역에서 사용하는 파생 알림이다.
 *
 * @param type 알림 종류
 * @param severity 알림 심각도
 * @param title 관리자에게 표시할 제목
 * @param count 알림 대상 수
 * @param targetUrl 상세 확인 화면 경로
 */
public record DashboardAlertResponse(
        String type,
        String severity,
        String title,
        long count,
        String targetUrl
) {
}
