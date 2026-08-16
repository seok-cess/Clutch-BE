package com.clutch.coupon.event.domain;

/**
 * 쿠폰 이벤트 상태
 */
public enum CouponEventStatus {
    /** 트리거 발생을 기다리는 상태. */
    READY,
    /** 쿠폰 신청을 받고 있는 상태. */
    OPEN,
    /** 쿠폰 신청이 정상 종료된 상태. */
    CLOSED,
    /** 이벤트가 취소된 상태. */
    CANCELLED
}
