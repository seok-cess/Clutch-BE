package com.clutch.coupon.admin.dashboard.repository;

/**
 * 관리자 페이지 운영 홈에서 Redis 재고를 일괄 조회할 진행 중 이벤트 항목이다.
 *
 * @param couponEventId 쿠폰 이벤트 ID
 * @param couponEventItemId 쿠폰 이벤트 항목 ID
 */
public record OpenEventItemRow(
        Long couponEventId,
        Long couponEventItemId
) {
}
