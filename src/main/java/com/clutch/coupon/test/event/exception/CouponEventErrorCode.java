package com.clutch.coupon.test.event.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 수동 쿠폰 발급 테스트에서 사용하는 오류 코드. */
@Getter
@RequiredArgsConstructor
public enum CouponEventErrorCode {

    COUPON_EVENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "쿠폰 이벤트를 찾을 수 없습니다."
    ),
    COUPON_EVENT_NOT_OPENABLE(
            HttpStatus.CONFLICT,
            "대기 상태의 쿠폰 이벤트만 오픈할 수 있습니다."
    ),
    COUPON_EVENT_ALREADY_OPEN(
            HttpStatus.CONFLICT,
            "이미 진행 중인 쿠폰 이벤트 회차가 있습니다."
    ),
    COUPON_EVENT_STOCK_EXHAUSTED(
            HttpStatus.CONFLICT,
            "남아 있는 쿠폰 재고가 없어 이벤트를 오픈할 수 없습니다."
    );

    private final HttpStatus httpStatus;
    private final String message;
}
