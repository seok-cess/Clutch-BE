package com.clutch.coupon.claim.outbox;

/**
 * 쿠폰 발급 Outbox 상태
 */
public enum CouponClaimOutboxStatus {

    /**
     * 발행 대기
     */
    PENDING,

    /**
     * 발행 완료
     */
    SENT
}