package com.clutch.coupon.claim.exception;

import org.springframework.http.HttpStatus;

/**
 * 쿠폰 발급 요청 오류 코드
 */
public enum CouponClaimErrorCode {

    COUPON_EVENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "존재하지 않는 쿠폰 이벤트입니다."
    ),
    
    COUPON_CLAIM_REQUEST_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "존재하지 않는 쿠폰 발급 요청입니다."
    ),

    COUPON_EVENT_ITEM_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "존재하지 않는 쿠폰 이벤트 항목입니다."
    ),

    COUPON_BENEFIT_NOT_FOUND(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "쿠폰 혜택 정보를 조회할 수 없습니다."
    ),

    COUPON_EVENT_OCCURRENCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "존재하지 않는 쿠폰 이벤트 회차입니다."
    ),

    COUPON_EVENT_ITEM_NOT_AVAILABLE(
            HttpStatus.CONFLICT,
            "현재 발급 가능한 쿠폰 혜택이 없습니다."
    ),

    COUPON_EVENT_NOT_OPEN(
            HttpStatus.CONFLICT,
            "진행 중인 쿠폰 이벤트가 아닙니다."
    ),

    COUPON_ALREADY_CLAIMED(
            HttpStatus.CONFLICT,
            "이미 발급을 요청한 쿠폰입니다."
    ),

    COUPON_STOCK_EXHAUSTED(
            HttpStatus.CONFLICT,
            "쿠폰 재고가 소진되었습니다."
    ),
    COUPON_STOCK_NOT_INITIALIZED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "쿠폰 재고가 준비되지 않았습니다."
    ),
    COUPON_STOCK_READ_FAILED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "쿠폰 재고를 조회할 수 없습니다."
    ),
    COUPON_REDIS_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "쿠폰 발급 시스템에 일시적으로 연결할 수 없습니다."
    ),
    COUPON_STOCK_RECOVERING(
            HttpStatus.SERVICE_UNAVAILABLE,
            "쿠폰 재고를 복구하고 있습니다."
    ),
    COUPON_STOCK_RECOVERY_FAILED(
            HttpStatus.SERVICE_UNAVAILABLE,
            "쿠폰 재고 복구에 실패했습니다."
    ),
    COUPON_STOCK_INCONSISTENT(
            HttpStatus.SERVICE_UNAVAILABLE,
            "쿠폰 발급 데이터가 일치하지 않습니다."
    ),
    INVALID_ADMIN_CLAIM_QUERY(
            HttpStatus.BAD_REQUEST,
            "관리자 발급 내역 조회 조건이 올바르지 않습니다."
    ),
    INVALID_ADMIN_STATISTICS_QUERY(
            HttpStatus.BAD_REQUEST,
            "관리자 쿠폰 통계 조회 조건이 올바르지 않습니다."
    );


    private final HttpStatus httpStatus;
    private final String message;

    /**
     * 쿠폰 발급 요청 오류 코드 생성자
     *
     * @param httpStatus HTTP 상태
     * @param message 오류 메시지
     */
    CouponClaimErrorCode(
            HttpStatus httpStatus,
            String message
    ) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    /**
     * HTTP 상태 반환
     *
     * @return HTTP 상태
     */
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    /**
     * 오류 메시지 반환
     *
     * @return 오류 메시지
     */
    public String getMessage() {
        return message;
    }
}
