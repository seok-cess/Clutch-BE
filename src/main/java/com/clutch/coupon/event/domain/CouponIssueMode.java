package com.clutch.coupon.event.domain;

/**
 * 쿠폰 이벤트의 선착순 발급 방식.
 */
public enum CouponIssueMode {
    /** 하나의 쿠폰 종류를 설정 수량만큼 동일하게 발급한다. */
    SINGLE_FIRST_COME,
    /** 경과 시간별 단계에 따라 서로 다른 혜택의 쿠폰을 발급한다. */
    PHASED_FIRST_COME
}
