package com.clutch.coupon.admin.dashboard.dto;

import java.time.LocalDate;

/**
 * 관리자 페이지 운영 홈 차트에서 사용하는 날짜별 쿠폰 발급 결과다.
 *
 * @param date Asia/Seoul 기준 날짜
 * @param issuedCount 발급 성공 수
 * @param failedCount 발급 처리 실패와 Redis·조건 검증 거절 수
 */
public record DailyIssuanceResponse(
        LocalDate date,
        long issuedCount,
        long failedCount
) {
}
