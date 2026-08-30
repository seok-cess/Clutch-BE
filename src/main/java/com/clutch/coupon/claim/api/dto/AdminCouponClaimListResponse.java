package com.clutch.coupon.claim.api.dto;

import java.util.List;

/**
 * 번호형 페이지네이션을 적용한 관리자 쿠폰 발급 내역 응답.
 *
 * <p>{@code page}는 0부터 시작하며, 전체 건수와 페이지 수를 함께 제공해
 * 관리자가 특정 페이지와 마지막 페이지로 직접 이동할 수 있게 한다.</p>
 *
 * @param claims 현재 페이지의 발급 내역
 * @param page 현재 페이지 번호, 0부터 시작
 * @param size 한 페이지의 최대 발급 내역 수
 * @param totalElements 조회 조건에 맞는 전체 발급 내역 수
 * @param totalPages 전체 페이지 수
 * @param hasPrevious 이전 페이지 존재 여부
 * @param hasNext 다음 페이지 존재 여부
 */
public record AdminCouponClaimListResponse(
        List<AdminCouponClaimResponse> claims,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext
) {
}
