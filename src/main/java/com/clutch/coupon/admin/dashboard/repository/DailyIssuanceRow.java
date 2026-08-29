package com.clutch.coupon.admin.dashboard.repository;

import java.time.LocalDate;

/**
 * 관리자 페이지 운영 홈의 일별 쿠폰 발급 집계 조회 결과다.
 *
 * @param date Asia/Seoul 기준 발급 요청 날짜
 * @param issuedCount 발급 성공 수
 * @param failedCount 발급 실패 수
 */
public record DailyIssuanceRow(
        LocalDate date,
        long issuedCount,
        long failedCount
) {
}
