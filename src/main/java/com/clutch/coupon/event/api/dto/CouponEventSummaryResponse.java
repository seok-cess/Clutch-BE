package com.clutch.coupon.event.api.dto;

import com.clutch.coupon.event.domain.CouponEventStatus;
import com.clutch.coupon.event.domain.CouponIssueMode;

import java.time.LocalDateTime;

/**
 * 관리자 쿠폰 이벤트 목록에 표시할 요약 정보.
 *
 * @param couponEventId 쿠폰 이벤트 ID
 * @param eventName 이벤트 이름
 * @param esportsMatchId 이벤트를 적용할 경기 ID
 * @param triggerType 이벤트 시작 조건인 경기 트리거 종류
 * @param issueMode 쿠폰 발급 방식
 * @param eventStatus 이벤트 상태
 * @param claimWindowSeconds 쿠폰 신청 가능 시간(초)
 * @param totalQuantity 전체 쿠폰 수량
 * @param issuedQuantity 발급 성공 수량
 * @param remainingQuantity 잔여 수량
 * @param createdAt 이벤트 생성 시각
 */
public record CouponEventSummaryResponse(
        Long couponEventId,
        String eventName,
        Long esportsMatchId,
        String triggerType,
        CouponIssueMode issueMode,
        CouponEventStatus eventStatus,
        int claimWindowSeconds,
        long totalQuantity,
        long issuedQuantity,
        long remainingQuantity,
        LocalDateTime createdAt
) {
}
