package com.clutch.coupon.integrity.service;

public class CouponIntegrityException extends RuntimeException {
    private final CouponIntegrityErrorCode errorCode;

    public CouponIntegrityException(CouponIntegrityErrorCode errorCode) {
        this(errorCode, errorCode.message());
    }

    public CouponIntegrityException(CouponIntegrityErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public CouponIntegrityErrorCode getErrorCode() {
        return errorCode;
    }
}
