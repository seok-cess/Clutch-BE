package com.clutch.coupon.event.api.dto;

import com.clutch.coupon.event.domain.CouponEventOccurrenceStatus;

import java.time.LocalDateTime;

/**
 * 경기 트리거로 실제 열린 쿠폰 이벤트 발생 회차 정보.
 *
 * @param couponEventOccurrenceId 이벤트 발생 회차 ID
 * @param matchEventId 발생 원인이 된 경기 이벤트 ID
 * @param sourceEventKey 외부 이벤트의 중복 수신을 식별하는 키
 * @param gameTimeSeconds 경기 시작 후 이벤트가 발생한 시점(초)
 * @param sourceOccurredAt 외부 경기 데이터 기준 발생 시각
 * @param detectedAt 시스템이 트리거를 감지한 시각
 * @param openedAt 쿠폰 신청을 시작한 시각
 * @param expiresAt 쿠폰 신청이 만료되는 시각
 * @param closedAt 실제 종료 시각
 * @param occurrenceStatus 발생 회차의 진행 상태
 * @param closeReason 종료 또는 취소 사유
 */
public record CouponEventOccurrenceResponse(
        Long couponEventOccurrenceId,
        Long matchEventId,
        String sourceEventKey,
        Integer gameTimeSeconds,
        LocalDateTime sourceOccurredAt,
        LocalDateTime detectedAt,
        LocalDateTime openedAt,
        LocalDateTime expiresAt,
        LocalDateTime closedAt,
        CouponEventOccurrenceStatus occurrenceStatus,
        String closeReason
) {
}
