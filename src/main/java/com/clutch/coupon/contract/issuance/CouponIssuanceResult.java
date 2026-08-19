package com.clutch.coupon.contract.issuance;

/**
 * 동기 쿠폰 발급 결과
 *
 * @param couponId 발급 쿠폰 식별자
 */
public record CouponIssuanceResult(
        Long couponId
) {
}