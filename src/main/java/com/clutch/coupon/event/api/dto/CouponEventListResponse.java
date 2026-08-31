package com.clutch.coupon.event.api.dto;

import java.util.List;

/**
 * 번호형 페이지네이션을 적용한 쿠폰 이벤트 목록 조회 결과.
 *
 * <p>{@code page}는 0부터 시작하며, 관리자 화면은 {@code totalPages}를
 * 사용해 처음·현재 주변·마지막 페이지 번호를 구성한다.</p>
 *
 * @param events 현재 페이지의 이벤트 목록
 * @param page 현재 페이지 번호, 0부터 시작
 * @param size 한 페이지의 최대 이벤트 수
 * @param totalElements 조회 조건에 맞는 전체 이벤트 수
 * @param totalPages 전체 페이지 수
 * @param hasPrevious 이전 페이지 존재 여부
 * @param hasNext 다음 페이지 존재 여부
 */
public record CouponEventListResponse(
        List<CouponEventSummaryResponse> events,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasPrevious,
        boolean hasNext
) {
}
