package com.clutch.coupon.event.api.dto;

public record CouponEventItemDetailResponse(
        Long couponEventItemId,
        Long couponTypeId,
        int quantity,
        int successCount,
        int remainingQuantity,
        Long phaseId,
        Integer phaseSequence,
        Integer openOffsetSeconds
) {
}
