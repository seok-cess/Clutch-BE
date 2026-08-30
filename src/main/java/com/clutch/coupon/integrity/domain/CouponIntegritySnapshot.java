package com.clutch.coupon.integrity.domain;

import java.time.LocalDateTime;
import java.util.List;

public record CouponIntegritySnapshot(
        LocalDateTime asOfUtc,
        long userCount,
        long claimRequestCount,
        long userCouponCount,
        long couponEventCount,
        long occurrenceCount,
        long eventItemCount,
        CouponIntegrityFingerprint claimRequestFingerprint,
        CouponIntegrityFingerprint userCouponFingerprint,
        List<CouponIntegrityResult> results,
        IntegrityVerdict overallVerdict
) {
    public long count(IntegrityVerdict verdict) {
        return results.stream().filter(result -> result.verdict() == verdict).count();
    }
}
