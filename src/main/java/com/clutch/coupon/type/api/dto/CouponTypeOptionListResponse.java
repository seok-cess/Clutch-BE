package com.clutch.coupon.type.api.dto;

import java.util.List;

/**
 * 이벤트 생성용 활성 쿠폰 종류 커서 페이지 응답.
 *
 * @param options 현재 페이지의 선택 항목
 * @param nextCursor 다음 페이지 조회에 사용할 마지막 쿠폰 종류 ID
 * @param hasNext 다음 페이지 존재 여부
 */
public record CouponTypeOptionListResponse(
        List<CouponTypeOptionResponse> options,
        Long nextCursor,
        boolean hasNext
) {
}
