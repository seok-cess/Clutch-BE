package com.clutch.coupon.integrity.api.dto;

import com.clutch.coupon.integrity.domain.IntegrityExecutionStatus;

import java.time.LocalDateTime;

public record CouponIntegrityStartResponse(
        Long checkId,
        IntegrityExecutionStatus executionStatus,
        LocalDateTime startedAt
) {
}
