package com.clutch.coupon.type.api.dto;

import com.clutch.coupon.type.domain.CouponDiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 관리자 쿠폰 종류 등록 요청.
 *
 * @param couponName 쿠폰 이름
 * @param discountType 할인 계산 방식
 * @param discountValue 할인율 또는 할인 금액
 */
public record CouponTypeCreateRequest(
        @NotBlank(message = "쿠폰 이름은 필수입니다.")
        @Size(max = 100, message = "쿠폰 이름은 100자 이하여야 합니다.")
        String couponName,

        @NotNull(message = "할인 유형은 필수입니다.")
        CouponDiscountType discountType,

        @NotNull(message = "할인 값은 필수입니다.")
        @DecimalMin(value = "0", inclusive = false,
                message = "할인 값은 0보다 커야 합니다.")
        @Digits(integer = 8, fraction = 2,
                message = "할인 값은 정수 8자리, 소수 2자리 이하여야 합니다.")
        BigDecimal discountValue
) {
}
