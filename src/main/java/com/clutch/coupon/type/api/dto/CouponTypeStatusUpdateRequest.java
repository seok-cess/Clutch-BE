package com.clutch.coupon.type.api.dto;

import com.clutch.coupon.type.domain.CouponTypeStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 관리자 쿠폰 종류 상태 변경 요청.
 *
 * @param status 변경할 쿠폰 종류 상태
 */
public record CouponTypeStatusUpdateRequest(
        @NotNull(message = "쿠폰 종류 상태는 필수입니다.")
        CouponTypeStatus status
) {
}
