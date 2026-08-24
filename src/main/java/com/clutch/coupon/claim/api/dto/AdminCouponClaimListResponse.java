package com.clutch.coupon.claim.api.dto;

import java.util.List;

/**
 * 관리자 쿠폰 발급 내역 커서 페이지 응답.
 *
 * @param claims 현재 페이지의 발급 내역
 * @param nextCursor 다음 페이지 조회에 사용할 마지막 발급 요청 ID
 * @param hasNext 다음 페이지 존재 여부
 */
public record AdminCouponClaimListResponse(
        List<AdminCouponClaimResponse> claims,
        Long nextCursor,
        boolean hasNext
) {
}
