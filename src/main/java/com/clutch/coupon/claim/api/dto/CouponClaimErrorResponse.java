package com.clutch.coupon.claim.api.dto;

import com.clutch.coupon.claim.exception.CouponClaimErrorCode;

/**
 * 쿠폰 발급 요청 오류 응답 DTO
 *
 * @param code 오류 코드
 * @param message 오류 메시지
 */
public record CouponClaimErrorResponse(
        String code,
        String message
) {

    /**
     * 쿠폰 발급 요청 오류 응답 생성
     *
     * @param errorCode 쿠폰 발급 요청 오류 코드
     * @return 쿠폰 발급 요청 오류 응답
     */
    public static CouponClaimErrorResponse from(
            CouponClaimErrorCode errorCode
    ) {
        return new CouponClaimErrorResponse(
                errorCode.name(),
                errorCode.getMessage()
        );
    }

    /**
     * 요청 검증 오류 응답 생성
     *
     * @param message 오류 메시지
     * @return 쿠폰 발급 요청 오류 응답
     */
    public static CouponClaimErrorResponse invalidRequest(
            String message
    ) {
        return new CouponClaimErrorResponse(
                "INVALID_REQUEST",
                message
        );
    }
}