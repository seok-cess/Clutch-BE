package com.clutch.wallet.web.dto;

import com.clutch.wallet.domain.UserCoupon;
import com.clutch.wallet.domain.UserCouponStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record CouponResponse(
    Long id,
    Long couponEventId,
    String couponCode,
    UserCouponStatus status,
    String discountType,
    BigDecimal discountValue,
    Instant expiresAt,
    Instant usedAt,
    Instant cancelledAt
){
    public static CouponResponse from(UserCoupon coupon){
        return new CouponResponse(
                coupon.getId(),
                coupon.getCouponEventId(),
                coupon.getCouponCode(),
                coupon.getStatus(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getExpiresAt(),
                coupon.getUsedAt(),
                coupon.getCancelledAt()
        );
    }
}