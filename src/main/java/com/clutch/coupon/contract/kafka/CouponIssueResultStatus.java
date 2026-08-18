package com.clutch.coupon.contract.kafka;

/**
 * 쿠폰 생성 결과 상태
 */
public enum CouponIssueResultStatus {

    /**
     * 쿠폰 생성 성공
     */
    SUCCEEDED,

    /**
     * 쿠폰 생성 실패
     */
    FAILED
}