package com.clutch.coupon.claim.api.dto;

import com.clutch.coupon.claim.domain.ClaimRequestStatus;
import com.clutch.coupon.type.domain.CouponDiscountType;
import com.clutch.wallet.domain.UserCouponStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 관리자 쿠폰 발급 내역의 한 행.
 *
 * @param claimRequestId 발급 요청 ID
 * @param requestedAt 요청 시각
 * @param completedAt 처리 완료 시각
 * @param couponEventId 쿠폰 이벤트 ID
 * @param eventName 이벤트 이름
 * @param triggerType 경기 트리거 문자열
 * @param couponEventOccurrenceId 이벤트 발생 회차 ID
 * @param userId 사용자 ID
 * @param maskedName 마스킹된 이름
 * @param maskedEmail 마스킹된 이메일
 * @param maskedPhoneNumber 마스킹된 전화번호
 * @param couponTypeId 쿠폰 종류 ID
 * @param couponName 쿠폰 이름
 * @param discountType 할인 유형
 * @param discountValue 할인율 또는 할인 금액
 * @param requestStatus 발급 요청 처리 상태
 * @param failureReason 발급 실패 사유
 * @param userCouponId 실제 발급 쿠폰 ID
 * @param couponStatus 발급 쿠폰의 현재 상태
 */
public record AdminCouponClaimResponse(
        Long claimRequestId,
        LocalDateTime requestedAt,
        LocalDateTime completedAt,
        Long couponEventId,
        String eventName,
        String triggerType,
        Long couponEventOccurrenceId,
        Long userId,
        String maskedName,
        String maskedEmail,
        String maskedPhoneNumber,
        Long couponTypeId,
        String couponName,
        CouponDiscountType discountType,
        BigDecimal discountValue,
        ClaimRequestStatus requestStatus,
        String failureReason,
        Long userCouponId,
        UserCouponStatus couponStatus
) {
}
