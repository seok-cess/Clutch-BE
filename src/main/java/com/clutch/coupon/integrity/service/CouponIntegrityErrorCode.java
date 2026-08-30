package com.clutch.coupon.integrity.service;

import org.springframework.http.HttpStatus;

public enum CouponIntegrityErrorCode {
    INTEGRITY_CHECK_ALREADY_RUNNING(HttpStatus.CONFLICT, "이미 쿠폰 정합성 검증이 실행 중입니다."),
    INTEGRITY_CHECK_NOT_FOUND(HttpStatus.NOT_FOUND, "쿠폰 정합성 검증 이력을 찾을 수 없습니다."),
    INTEGRITY_CHECK_EXECUTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "쿠폰 정합성 검증 실행을 시작하지 못했습니다."),
    INVALID_INTEGRITY_CHECK_LIST_CONDITION(HttpStatus.BAD_REQUEST, "검증 이력 조회 조건이 올바르지 않습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    CouponIntegrityErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public HttpStatus httpStatus() { return httpStatus; }
    public String message() { return message; }
}
