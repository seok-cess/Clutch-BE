package com.clutch.coupon.type.api.dto;

/**
 * 쿠폰 종류 관리자 API 오류 응답.
 *
 * @param code 오류 코드
 * @param message 오류 메시지
 */
public record CouponTypeErrorResponse(
        String code,
        String message
) {
}
