package com.clutch.coupon.event.exception;

import lombok.Getter;

/**
 * 쿠폰 이벤트 유스케이스 규칙 위반을 표현하는 비즈니스 예외.
 */
@Getter
public class CouponEventException extends RuntimeException {

    private final CouponEventErrorCode errorCode;

    /**
     * 오류 코드에 정의된 기본 메시지로 예외를 생성한다.
     *
     * @param errorCode 발생한 쿠폰 이벤트 오류 코드
     */
    public CouponEventException(CouponEventErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 상황에 맞는 상세 메시지로 예외를 생성한다.
     *
     * @param errorCode 발생한 쿠폰 이벤트 오류 코드
     * @param message 응답에 사용할 상세 메시지
     */
    public CouponEventException(
            CouponEventErrorCode errorCode,
            String message
    ) {
        super(message);
        this.errorCode = errorCode;
    }
}
