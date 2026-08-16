package com.clutch.coupon.event.api.dto;

/**
 * 쿠폰 종류별 재고와 발급 단계 상세 정보.
 *
 * @param couponEventItemId 쿠폰 이벤트 항목 ID
 * @param couponTypeId 쿠폰 종류 ID
 * @param quantity 최초 발급 가능 수량
 * @param successCount 발급 성공 수량
 * @param remainingQuantity 잔여 수량
 * @param phaseId 쿠폰 발급 단계 ID
 * @param phaseSequence 단계 실행 순서
 * @param openOffsetSeconds 이벤트 오픈 시점부터 단계 시작까지의 시간(초)
 */
public record CouponEventItemDetailResponse(
        Long couponEventItemId,
        Long couponTypeId,
        int quantity,
        int successCount,
        int remainingQuantity,
        Long phaseId,
        Integer phaseSequence,
        Integer openOffsetSeconds
) {
}
