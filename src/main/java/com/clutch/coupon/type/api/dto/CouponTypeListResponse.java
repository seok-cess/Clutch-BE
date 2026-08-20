package com.clutch.coupon.type.api.dto;

import java.util.List;

/**
 * 관리자 쿠폰 종류 커서 페이지 응답.
 *
 * @param couponTypes 현재 페이지의 쿠폰 종류 목록
 * @param nextCursor 다음 페이지 조회에 사용할 마지막 쿠폰 종류 ID
 * @param hasNext 다음 페이지 존재 여부
 */
public record CouponTypeListResponse(
        List<CouponTypeResponse> couponTypes,
        Long nextCursor,
        boolean hasNext
) {
}
