package com.clutch.coupon.event.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record CouponEventItemCreateRequest(
        @NotNull(message = "쿠폰 종류 ID는 필수입니다.")
        @Positive(message = "쿠폰 종류 ID는 양수여야 합니다.")
        Long couponTypeId,

        @NotNull(message = "쿠폰 수량은 필수입니다.")
        @Positive(message = "쿠폰 수량은 1개 이상이어야 합니다.")
        Integer quantity,

        @NotNull(message = "단계 오픈 시간은 필수입니다.")
        @PositiveOrZero(message = "단계 오픈 시간은 0초 이상이어야 합니다.")
        Integer openOffsetSeconds
) {
}
