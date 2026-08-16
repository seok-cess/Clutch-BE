package com.clutch.coupon.event.api.dto;

import com.clutch.coupon.event.domain.CouponEventStatus;
import com.clutch.coupon.event.domain.CouponIssueMode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 쿠폰 이벤트 설정 수정 결과.
 *
 * @param couponEventId 수정한 쿠폰 이벤트 ID
 * @param esportsMatchId 변경된 경기 ID
 * @param eventName 변경된 이벤트 이름
 * @param issueMode 변경된 쿠폰 발급 방식
 * @param triggerType 변경된 경기 트리거 종류
 * @param eventStatus 이벤트 상태
 * @param claimWindowSeconds 변경된 쿠폰 신청 가능 시간(초)
 * @param updatedAt 최종 수정 시각
 * @param items 교체된 쿠폰 항목과 단계 정보
 */
public record CouponEventUpdateResponse(
        Long couponEventId,
        Long esportsMatchId,
        String eventName,
        CouponIssueMode issueMode,
        String triggerType,
        CouponEventStatus eventStatus,
        int claimWindowSeconds,
        LocalDateTime updatedAt,
        List<CouponEventItemCreateResponse> items
) {
}
