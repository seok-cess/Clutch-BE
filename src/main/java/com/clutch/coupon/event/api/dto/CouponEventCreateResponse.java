package com.clutch.coupon.event.api.dto;

import com.clutch.coupon.event.domain.CouponEventStatus;
import com.clutch.coupon.event.domain.CouponIssueMode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 쿠폰 이벤트 등록 결과.
 *
 * @param couponEventId 생성된 쿠폰 이벤트 ID
 * @param esportsMatchId 이벤트를 적용할 경기 ID
 * @param eventName 이벤트 이름
 * @param issueMode 쿠폰 발급 방식
 * @param triggerType 이벤트 시작 조건인 경기 트리거 종류
 * @param eventStatus 생성된 이벤트의 상태
 * @param claimWindowSeconds 쿠폰 신청 가능 시간(초)
 * @param createdAt 이벤트 생성 시각
 * @param items 생성된 쿠폰 항목과 단계 정보
 */
public record CouponEventCreateResponse(
        Long couponEventId,
        Long esportsMatchId,
        String eventName,
        CouponIssueMode issueMode,
        String triggerType,
        CouponEventStatus eventStatus,
        int claimWindowSeconds,
        LocalDateTime createdAt,
        List<CouponEventItemCreateResponse> items
) {
}
