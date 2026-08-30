package com.clutch.coupon.integrity.api.dto;

import com.clutch.coupon.integrity.domain.IntegrityExecutionStatus;
import com.clutch.coupon.integrity.domain.IntegrityVerdict;

import java.time.LocalDateTime;
import java.util.List;

public record CouponIntegrityDetailResponse(
        Long checkId,
        IntegrityExecutionStatus executionStatus,
        IntegrityVerdict overallVerdict,
        Long requestedBy,
        LocalDateTime asOfUtc,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Long durationSeconds,
        Long userCount,
        Long claimRequestCount,
        Long userCouponCount,
        Long couponEventCount,
        Long occurrenceCount,
        Long eventItemCount,
        Long claimRequestMinId,
        Long claimRequestMaxId,
        Long claimRequestFingerprint,
        Long userCouponMinId,
        Long userCouponMaxId,
        Long userCouponFingerprint,
        Long checkCount,
        Long passCount,
        Long infoCount,
        Long warnCount,
        Long failCount,
        String errorCode,
        String errorMessage,
        List<CouponIntegrityResultResponse> results
) {
}
