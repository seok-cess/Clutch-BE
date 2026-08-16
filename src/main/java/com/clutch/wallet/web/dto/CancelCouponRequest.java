package com.clutch.wallet.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelCouponRequest(@NotBlank String reason) {
}
