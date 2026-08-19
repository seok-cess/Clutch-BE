package com.clutch.coupon.type.api.dto;

import com.clutch.coupon.type.domain.CouponDiscountType;
import com.clutch.coupon.type.domain.CouponTypeStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 관리자 쿠폰 종류 조회 결과.
 *
 * @param couponTypeId 쿠폰 종류 ID
 * @param couponName 쿠폰 이름
 * @param discountType 할인 계산 방식
 * @param discountValue 할인율 또는 할인 금액
 * @param status 신규 이벤트 선택 가능 상태
 * @param used 쿠폰 이벤트 사용 이력 존재 여부
 * @param createdAt 생성 시각
 * @param updatedAt 최종 수정 시각
 */
public record CouponTypeResponse(
        Long couponTypeId,
        String couponName,
        CouponDiscountType discountType,
        BigDecimal discountValue,
        CouponTypeStatus status,
        boolean used,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
