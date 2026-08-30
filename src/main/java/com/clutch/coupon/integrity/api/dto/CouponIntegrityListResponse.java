package com.clutch.coupon.integrity.api.dto;

import java.util.List;

public record CouponIntegrityListResponse(
        List<CouponIntegritySummaryResponse> items,
        Long nextCursor,
        boolean hasNext
) {
}
