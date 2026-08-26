package com.clutch.coupon.claim.service;

/**
 * 쿠폰 성공 수량 동기화 한 번의 처리 결과.
 *
 * @param scannedItemCount 실제 발급 수량과 비교한 이벤트 항목 수
 * @param updatedItemCount 성공 수량을 변경한 이벤트 항목 수
 */
public record CouponSuccessCountSynchronizationResult(
        int scannedItemCount,
        int updatedItemCount
) {
}
