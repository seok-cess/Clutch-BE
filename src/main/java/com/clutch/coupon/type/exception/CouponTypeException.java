package com.clutch.coupon.type.exception;

import lombok.Getter;

/**
 * 쿠폰 종류 관리 규칙 위반을 표현하는 비즈니스 예외.
 */
@Getter
public class CouponTypeException extends RuntimeException {

    private final CouponTypeErrorCode errorCode;

    /**
     * 오류 코드에 정의된 기본 메시지로 예외를 생성한다.
     *
     * @param errorCode 쿠폰 종류 오류 코드
     */
    public CouponTypeException(CouponTypeErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 호출 지점에서 지정한 상세 메시지로 예외를 생성한다.
     *
     * @param errorCode 쿠폰 종류 오류 코드
     * @param message 상세 오류 메시지
     */
    public CouponTypeException(
            CouponTypeErrorCode errorCode,
            String message
    ) {
        super(message);
        this.errorCode = errorCode;
    }
}
