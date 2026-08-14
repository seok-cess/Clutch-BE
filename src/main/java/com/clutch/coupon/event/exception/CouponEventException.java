package com.clutch.coupon.event.exception;

import lombok.Getter;

@Getter
public class CouponEventException extends RuntimeException {

    private final CouponEventErrorCode errorCode;

    public CouponEventException(CouponEventErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public CouponEventException(
            CouponEventErrorCode errorCode,
            String message
    ) {
        super(message);
        this.errorCode = errorCode;
    }
}
