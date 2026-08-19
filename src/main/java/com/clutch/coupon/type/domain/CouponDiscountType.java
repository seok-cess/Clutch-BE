package com.clutch.coupon.type.domain;

/**
 * 쿠폰 할인 계산 방식.
 */
public enum CouponDiscountType {
    /** 결제 금액에 일정 비율을 적용하는 정률 할인. */
    RATE,
    /** 결제 금액에서 일정 금액을 차감하는 정액 할인. */
    AMOUNT
}
