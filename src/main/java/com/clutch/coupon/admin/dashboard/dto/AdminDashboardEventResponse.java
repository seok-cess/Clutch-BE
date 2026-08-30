package com.clutch.coupon.admin.dashboard.dto;

import com.clutch.coupon.event.domain.CouponEventStatus;

import java.time.LocalDate;

/**
 * 관리자 페이지 운영 홈의 쿠폰 이벤트 표에서 사용하는 한 행이다.
 *
 * @param couponEventId 쿠폰 이벤트 ID
 * @param eventName 쿠폰 이벤트 이름
 * @param matchDate Asia/Seoul 기준 경기 예정일
 * @param matchName 양 팀을 조합한 경기명
 * @param totalQuantity 전체 쿠폰 수량
 * @param issuedQuantity 비동기 집계된 발급 성공 수량
 * @param remainingQuantity 비동기 집계 기준 잔여 수량
 * @param eventStatus 쿠폰 이벤트 저장 상태
 * @param statusLabel 관리자 화면에 표시할 상태 문구
 */
public record AdminDashboardEventResponse(
        Long couponEventId,
        String eventName,
        LocalDate matchDate,
        String matchName,
        long totalQuantity,
        long issuedQuantity,
        long remainingQuantity,
        CouponEventStatus eventStatus,
        String statusLabel
) {
}
