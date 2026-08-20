package com.clutch.coupon.type.api.dto;

import com.clutch.coupon.type.domain.CouponDiscountType;

import java.math.BigDecimal;

/**
 * 이벤트 생성 화면의 쿠폰 종류 선택 항목.
 *
 * @param couponTypeId 쿠폰 종류 ID
 * @param couponName 쿠폰 이름
 * @param discountType 할인 계산 방식
 * @param discountValue 할인율 또는 할인 금액
 */
public record CouponTypeOptionResponse(
        Long couponTypeId,
        String couponName,
        CouponDiscountType discountType,
        BigDecimal discountValue
) {
}
