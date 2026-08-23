package com.clutch.wallet.web.dto;

import com.clutch.wallet.domain.UserCoupon;
import com.clutch.wallet.domain.UserCouponStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 사용자 쿠폰 응답.
 *
 * @param id 사용자 쿠폰 ID
 * @param couponEventId 발급 근거가 된 쿠폰 이벤트 ID
 * @param couponCode 쿠폰 코드
 * @param status 쿠폰 상태
 * @param discountType 할인 유형
 * @param discountValue 할인 값
 * @param expiresAt 만료 시각
 * @param usedAt 사용 시각, 미사용 시 {@code null}
 * @param cancelledAt 취소 시각, 미취소 시 {@code null}
 */
public record CouponResponse(
    Long id,
    Long couponEventId,
    String couponCode,
    UserCouponStatus status,
    String discountType,
    BigDecimal discountValue,
    Instant expiresAt,
    Instant usedAt,
    Instant cancelledAt
){
    /**
     * 엔티티로부터 응답 DTO를 생성한다.
     *
     * @param coupon 변환할 사용자 쿠폰
     * @return 변환된 응답
     */
    public static CouponResponse from(UserCoupon coupon){
        return new CouponResponse(
                coupon.getId(),
                coupon.getCouponEventId(),
                coupon.getCouponCode(),
                coupon.getStatus(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getExpiresAt(),
                coupon.getUsedAt(),
                coupon.getCancelledAt()
        );
    }
}