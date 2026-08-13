package com.clutch.coupon.claim.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 쿠폰 발급 요청 생성 DTO
 *
 * @param couponEventItemId 쿠폰 이벤트 항목 식별자
 */
public record CouponClaimCreateRequest(

        @NotNull(message = "쿠폰 이벤트 항목 식별자는 필수입니다.")
        @Positive(message = "쿠폰 이벤트 항목 식별자는 양수여야 합니다.")
        Long couponEventItemId

) {
}