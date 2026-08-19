package com.clutch.coupon.event.exception;

import org.springframework.http.HttpStatus;

/**
 * 쿠폰 이벤트 API에서 사용하는 비즈니스 오류 코드와 HTTP 상태 매핑.
 */
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
    ),
    COUPON_EVENT_NOT_EDITABLE(
            HttpStatus.CONFLICT,
            "대기 상태의 쿠폰 이벤트만 수정할 수 있습니다."
    ),
    COUPON_EVENT_NOT_DELETABLE(
            HttpStatus.CONFLICT,
            "발생 또는 발급 이력이 없는 대기 상태의 쿠폰 이벤트만 삭제할 수 있습니다."
    ),
    COUPON_TYPE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "존재하지 않는 쿠폰 종류가 포함되어 있습니다."
    ),
    COUPON_TYPE_INACTIVE(
            HttpStatus.CONFLICT,
            "비활성 쿠폰 종류는 신규 쿠폰 이벤트에서 사용할 수 없습니다."
    );

    private final HttpStatus httpStatus;
    private final String message;

    CouponEventErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    /**
     * 오류에 대응하는 HTTP 상태를 반환한다.
     *
     * @return HTTP 상태
     */
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    /**
     * 오류의 기본 사용자 메시지를 반환한다.
     *
     * @return 기본 오류 메시지
     */
    public String getMessage() {
        return message;
    }
}
