package com.clutch.coupon.contract.issuance;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 동기 쿠폰 발급 명령
 *
 * @param claimId 쿠폰 발급 요청 식별자
 * @param userId 사용자 식별자
 * @param couponEventId 쿠폰 이벤트 식별자
 * @param couponEventOccurrenceId 쿠폰 이벤트 회차 식별자
 * @param couponEventItemId 쿠폰 이벤트 항목 식별자
 * @param discountType 할인 유형
 * @param discountValue 할인 값
 * @param expiresAt 쿠폰 만료 시각
 */
public record CouponIssuanceCommand(
        Long claimId,
        Long userId,
        Long couponEventId,
        Long couponEventOccurrenceId,
        Long couponEventItemId,
        String discountType,
        BigDecimal discountValue,
        Instant expiresAt
) {
}