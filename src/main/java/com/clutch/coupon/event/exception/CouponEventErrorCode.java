package com.clutch.coupon.event.exception;

import org.springframework.http.HttpStatus;

public enum CouponEventErrorCode {
    COUPON_EVENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "존재하지 않는 쿠폰 이벤트입니다."
    ),
    INVALID_EVENT_CONFIGURATION(
            HttpStatus.BAD_REQUEST,
            "쿠폰 이벤트 설정이 올바르지 않습니다."
    ),
    COUPON_EVENT_DUPLICATED(
            HttpStatus.CONFLICT,
            "같은 경기와 트리거로 등록된 쿠폰 이벤트가 이미 존재합니다."
    );

    private final HttpStatus httpStatus;
    private final String message;

    CouponEventErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
