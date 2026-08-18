package com.clutch.coupon.test.event.exception;

import lombok.Getter;

/** 수동 쿠폰 발급 테스트 규칙 위반을 나타내는 예외. */
@Getter
public class CouponEventException extends RuntimeException {

    private final CouponEventErrorCode errorCode;

    public CouponEventException(CouponEventErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
