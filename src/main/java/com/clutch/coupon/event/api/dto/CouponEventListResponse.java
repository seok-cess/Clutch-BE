package com.clutch.coupon.event.api.dto;

import java.util.List;

/**
 * 커서 기반 쿠폰 이벤트 목록 조회 결과.
 *
 * @param events 현재 페이지의 이벤트 목록
 * @param nextCursor 다음 페이지 조회에 사용할 마지막 이벤트 ID
 * @param hasNext 다음 페이지 존재 여부
 */
public record CouponEventListResponse(
        List<CouponEventSummaryResponse> events,
        Long nextCursor,
        boolean hasNext
) {
}
