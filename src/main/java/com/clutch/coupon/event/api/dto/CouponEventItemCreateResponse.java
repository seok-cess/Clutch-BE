package com.clutch.coupon.event.api.dto;

/**
 * 생성된 쿠폰 항목과 발급 단계 정보.
 *
 * @param phaseId 쿠폰 발급 단계 ID
 * @param couponEventItemId 쿠폰 이벤트 항목 ID
 * @param couponTypeId 쿠폰 종류 ID
 * @param quantity 발급 가능 수량
 * @param successCount 현재 발급 성공 수량
 * @param phaseSequence 단계 실행 순서
 * @param openOffsetSeconds 이벤트 오픈 시점부터 단계 시작까지의 시간(초)
 */
public record CouponEventItemCreateResponse(
        Long phaseId,
        Long couponEventItemId,
        Long couponTypeId,
        int quantity,
        int successCount,
        int phaseSequence,
        int openOffsetSeconds
) {
}
