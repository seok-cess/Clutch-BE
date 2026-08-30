package com.clutch.coupon.admin.dashboard.repository;

import com.clutch.coupon.event.domain.CouponEventStatus;

import java.time.LocalDateTime;

/**
 * 관리자 페이지 운영 홈의 쿠폰 이벤트 표를 위한 DB 조회 결과다.
 *
 * @param couponEventId 쿠폰 이벤트 ID
 * @param eventName 쿠폰 이벤트 이름
 * @param eventStatus 쿠폰 이벤트 상태
 * @param scheduledAt 경기 예정 시각(UTC)
 * @param firstTeamName 첫 번째 팀 이름
 * @param secondTeamName 두 번째 팀 이름
 * @param totalQuantity 전체 쿠폰 수량
 * @param issuedQuantity 비동기 집계된 발급 성공 수량
 */
public record AdminDashboardEventRow(
        Long couponEventId,
        String eventName,
        CouponEventStatus eventStatus,
        LocalDateTime scheduledAt,
        String firstTeamName,
        String secondTeamName,
        long totalQuantity,
        long issuedQuantity
) {
}
