package com.clutch.coupon.integrity.domain;

public record CouponIntegrityResult(
        String checkCode,
        IntegrityVerdict severity,
        IntegrityVerdict verdict,
        long violationCount,
        String description,
        int displayOrder
) {
}
