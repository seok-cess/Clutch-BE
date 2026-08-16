package com.clutch.coupon.event.domain;

/**
 * 쿠폰 이벤트 회차 상태
 */
public enum CouponEventOccurrenceStatus {
    /** 쿠폰 신청을 받고 있는 상태. */
    OPEN,
    /** 정상적으로 종료된 상태. */
    CLOSED,
    /** 운영상 사유로 취소된 상태. */
    CANCELLED
}
