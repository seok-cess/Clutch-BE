package com.clutch.wallet.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 관리자 쿠폰 취소 요청.
 *
 * @param reason 취소 사유
 */
public record CancelCouponRequest(@NotBlank String reason) {
}
