package com.clutch.coupon.claim.outbox;

import java.math.BigDecimal;

/**
 * 쿠폰 혜택 스냅샷
 *
 * @param discountType 할인 유형
 * @param discountValue 할인 값
 */
public record CouponBenefitSnapshot(
        String discountType,
        BigDecimal discountValue
) {
}