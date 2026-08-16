package com.clutch.coupon.event.api.dto;

/**
 * 쿠폰 이벤트 API 오류 응답.
 *
 * @param code 클라이언트가 분기 처리할 수 있는 오류 코드
 * @param message 오류 원인을 설명하는 메시지
 */
public record CouponEventErrorResponse(
        String code,
        String message
) {
}
