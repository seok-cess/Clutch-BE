package com.clutch.coupon.integrity.api.dto;

import com.clutch.coupon.integrity.domain.IntegrityExecutionStatus;
import com.clutch.coupon.integrity.domain.IntegrityVerdict;

import java.time.LocalDateTime;

public record CouponIntegritySummaryResponse(
        Long checkId,
        IntegrityExecutionStatus executionStatus,
        IntegrityVerdict overallVerdict,
        Long requestedBy,
        LocalDateTime asOfUtc,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Long durationSeconds,
        Long claimRequestCount,
        Long userCouponCount,
        Long checkCount,
        Long passCount,
        Long infoCount,
        Long warnCount,
        Long failCount
) {
}
