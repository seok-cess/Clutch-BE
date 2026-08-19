package com.clutch.coupon.type.exception;

import org.springframework.http.HttpStatus;

/**
 * 쿠폰 종류 관리자 API 오류 코드.
 */
public enum CouponTypeErrorCode {
    /** 요청한 쿠폰 종류가 존재하지 않음. */
    COUPON_TYPE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "존재하지 않는 쿠폰 종류입니다."
    ),
    /** 쿠폰 이름, 할인 방식 또는 할인 값이 도메인 규칙에 맞지 않음. */
    INVALID_COUPON_TYPE_CONFIGURATION(
            HttpStatus.BAD_REQUEST,
            "쿠폰 종류 설정이 올바르지 않습니다."
    ),
    /** 이벤트 사용 이력이 있어 쿠폰 혜택 정의를 수정할 수 없음. */
    COUPON_TYPE_NOT_EDITABLE(
            HttpStatus.CONFLICT,
            "이벤트에서 사용된 쿠폰 종류의 혜택은 수정할 수 없습니다."
    ),
    /** 이벤트 사용 이력이 있어 쿠폰 종류를 물리 삭제할 수 없음. */
    COUPON_TYPE_NOT_DELETABLE(
            HttpStatus.CONFLICT,
            "이벤트에서 사용된 쿠폰 종류는 삭제할 수 없습니다."
    ),
    /** 비활성 쿠폰 종류를 신규 쿠폰 이벤트에서 사용하려 함. */
    COUPON_TYPE_INACTIVE(
            HttpStatus.CONFLICT,
            "비활성 쿠폰 종류는 신규 이벤트에서 사용할 수 없습니다."
    );

    private final HttpStatus httpStatus;
    private final String message;

    CouponTypeErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    /**
     * 오류 응답에 사용할 HTTP 상태를 반환한다.
     *
     * @return 오류에 대응하는 HTTP 상태
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
