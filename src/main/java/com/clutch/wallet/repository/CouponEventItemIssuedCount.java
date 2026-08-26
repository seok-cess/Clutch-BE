package com.clutch.wallet.repository;

/** 쿠폰 이벤트 항목별 실제 발급 수량 집계 결과. */
public interface CouponEventItemIssuedCount {

    /** 쿠폰 이벤트 항목 식별자 */
    Long getCouponEventItemId();

    /** 실제 발급된 사용자 쿠폰 수 */
    long getIssuedCouponCount();
}
