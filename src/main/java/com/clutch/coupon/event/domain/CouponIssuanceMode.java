package com.clutch.coupon.event.domain;

/**
 * 쿠폰 발급 방식
 */
public enum CouponIssuanceMode {

    /**
     * 일반 선착순 발급
     */
    STANDARD,

    /**
     * 시간 차등 발급
     */
    TIME_TIERED
}