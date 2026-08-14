package com.clutch.betting.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record BetCreateRequest(
        @NotBlank(message = "선택 팀 ID는 필수입니다.")
        String selectedTeamId,

        @Min(value = 1_000, message = "배팅 금액은 1,000포인트 이상이어야 합니다.")
        @Max(value = 100_000, message = "배팅 금액은 100,000포인트 이하여야 합니다.")
        long amount
) {
}
