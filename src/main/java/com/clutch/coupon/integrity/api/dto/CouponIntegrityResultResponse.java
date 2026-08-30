package com.clutch.coupon.integrity.api.dto;

import com.clutch.coupon.integrity.domain.IntegrityVerdict;

public record CouponIntegrityResultResponse(
        String checkCode,
        IntegrityVerdict severity,
        IntegrityVerdict verdict,
        long violationCount,
        String description
) {
}
