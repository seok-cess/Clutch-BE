package com.clutch.coupon.claim.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 관리자 발급 내역 조인 쿼리 결과.
 *
 * <p>개인정보 원문은 저장소와 서비스 사이에서만 전달되며 서비스에서
 * 마스킹된 후 API 응답으로 변환된다.</p>
 *
 * @param claimRequestId 발급 요청 ID
 * @param requestedAt 발급 요청 시각
 * @param completedAt 발급 처리 완료 시각
 * @param couponEventId 쿠폰 이벤트 ID
 * @param eventName 이벤트 이름
 * @param triggerType 경기 트리거 문자열
 * @param couponEventOccurrenceId 이벤트 발생 회차 ID
 * @param userId 사용자 ID
 * @param userName 사용자 이름 원문
 * @param userEmail 사용자 이메일 원문
 * @param userPhoneNumber 사용자 전화번호 원문
 * @param couponTypeId 쿠폰 종류 ID
 * @param couponName 쿠폰 이름
 * @param discountType 할인 유형 문자열
 * @param discountValue 할인율 또는 할인 금액
 * @param requestStatus 발급 요청 상태 문자열
 * @param failureReason 발급 실패 사유
 * @param userCouponId 실제 발급 쿠폰 ID
 * @param couponStatus 실제 발급 쿠폰 상태 문자열
 */
public record AdminCouponClaimRow(
        Long claimRequestId,
        LocalDateTime requestedAt,
        LocalDateTime completedAt,
        Long couponEventId,
        String eventName,
        String triggerType,
        Long couponEventOccurrenceId,
        Long userId,
        String userName,
        String userEmail,
        String userPhoneNumber,
        Long couponTypeId,
        String couponName,
        String discountType,
        BigDecimal discountValue,
        String requestStatus,
        String failureReason,
        Long userCouponId,
        String couponStatus
) {
}
