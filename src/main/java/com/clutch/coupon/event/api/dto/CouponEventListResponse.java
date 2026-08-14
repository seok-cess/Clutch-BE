package com.clutch.coupon.event.api.dto;

import java.util.List;

public record CouponEventListResponse(
        List<CouponEventSummaryResponse> events,
        Long nextCursor,
        boolean hasNext
) {
}
