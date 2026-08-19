package com.clutch.coupon.type.domain;

/**
 * 신규 쿠폰 이벤트에서 쿠폰 종류를 선택할 수 있는 상태.
 */
public enum CouponTypeStatus {
    /** 신규 쿠폰 이벤트에서 선택할 수 있는 상태. */
    ACTIVE,
    /** 신규 쿠폰 이벤트에서는 선택할 수 없는 상태. */
    INACTIVE
}
