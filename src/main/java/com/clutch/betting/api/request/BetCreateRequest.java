package com.clutch.betting.api.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 배팅 대상 팀과 포인트를 전달하는 등록 요청이다.
 *
 * @param selectedTeamId 사용자가 선택한 외부 팀 ID
 * @param amount 배팅할 포인트
 */
public record BetCreateRequest(
        @NotBlank(message = "선택 팀 ID는 필수입니다.")
        String selectedTeamId,

        @Min(value = 1_000, message = "배팅 금액은 1,000포인트 이상이어야 합니다.")
        @Max(value = 100_000, message = "배팅 금액은 100,000포인트 이하여야 합니다.")
        long amount
) {
}
