package com.clutch.wallet.web.dto;

import java.util.List;

/**
 * 커서 기반 페이징을 적용한 쿠폰 목록 응답.
 *
 * @param items 조회된 쿠폰 목록
 * @param nextCursor 다음 페이지 조회에 사용할 커서, 다음 페이지가 없으면 {@code null}
 * @param hasNext 다음 페이지 존재 여부
 */
public record CouponPageResponse (
    List<CouponResponse> items,
    String nextCursor,
    boolean hasNext
){}
