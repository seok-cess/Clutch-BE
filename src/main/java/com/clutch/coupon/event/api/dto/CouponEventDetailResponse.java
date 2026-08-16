package com.clutch.coupon.event.api.dto;

import com.clutch.coupon.event.domain.CouponEventStatus;
import com.clutch.coupon.event.domain.CouponIssueMode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 쿠폰 이벤트 상세 조회 결과.
 *
 * @param couponEventId 쿠폰 이벤트 ID
 * @param esportsMatchId 이벤트를 적용할 경기 ID
 * @param eventName 이벤트 이름
 * @param issueMode 쿠폰 발급 방식
 * @param triggerType 이벤트 시작 조건인 경기 트리거 종류
 * @param eventStatus 이벤트 상태
 * @param claimWindowSeconds 쿠폰 신청 가능 시간(초)
 * @param totalQuantity 전체 쿠폰 수량
 * @param issuedQuantity 현재까지 발급한 수량
 * @param remainingQuantity 전체 잔여 수량
 * @param createdAt 이벤트 생성 시각
 * @param updatedAt 이벤트 최종 수정 시각
 * @param items 쿠폰 종류별 수량과 단계 정보
 * @param latestOccurrence 가장 최근에 열린 이벤트 발생 회차, 없으면 {@code null}
 */
public record CouponEventDetailResponse(
        Long couponEventId,
        Long esportsMatchId,
        String eventName,
        CouponIssueMode issueMode,
        String triggerType,
        CouponEventStatus eventStatus,
        int claimWindowSeconds,
        long totalQuantity,
        long issuedQuantity,
        long remainingQuantity,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<CouponEventItemDetailResponse> items,
        CouponEventOccurrenceResponse latestOccurrence
) {
}
