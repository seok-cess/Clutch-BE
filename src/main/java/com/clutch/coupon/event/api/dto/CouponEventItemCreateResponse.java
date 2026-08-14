package com.clutch.coupon.event.api.dto;

public record CouponEventItemCreateResponse(
        Long phaseId,
        Long couponEventItemId,
        Long couponTypeId,
        int quantity,
        int successCount,
        int phaseSequence,
        int openOffsetSeconds
) {
}
