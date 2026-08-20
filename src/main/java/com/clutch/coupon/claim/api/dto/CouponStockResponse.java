package com.clutch.coupon.claim.api.dto;

/**
 * Redis 쿠폰 이벤트 항목 재고 응답
 *
 * @param couponEventItemId 쿠폰 이벤트 항목 식별자
 * @param remainingStock 남은 재고
 * @param exhausted 재고 소진 여부
 */
public record CouponStockResponse(
        Long couponEventItemId,
        long remainingStock,
        boolean exhausted
) {

    /** Redis 재고 응답 생성 */
    public static CouponStockResponse of(
            Long couponEventItemId,
            long remainingStock
    ) {
        return new CouponStockResponse(
                couponEventItemId,
                remainingStock,
                remainingStock == 0L
        );
    }
}
