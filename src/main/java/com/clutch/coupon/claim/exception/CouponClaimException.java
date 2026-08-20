package com.clutch.coupon.claim.exception;

/**
 * 쿠폰 발급 요청 예외
 */
public class CouponClaimException extends RuntimeException {

    private final CouponClaimErrorCode errorCode;

    /**
     * 쿠폰 발급 요청 예외 생성자
     *
     * @param errorCode 쿠폰 발급 요청 오류 코드
     */
    public CouponClaimException(
            CouponClaimErrorCode errorCode
    ) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** 하위 시스템 원인을 포함한 쿠폰 발급 예외 생성 */
    public CouponClaimException(
            CouponClaimErrorCode errorCode,
            Throwable cause
    ) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    /**
     * 쿠폰 발급 요청 오류 코드 반환
     *
     * @return 쿠폰 발급 요청 오류 코드
     */
    public CouponClaimErrorCode getErrorCode() {
        return errorCode;
    }
}
