package com.clutch.betting.api.request;

import com.clutch.betting.exception.BettingErrorCode;
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
        @NotBlank(message = BettingErrorCode.Message.SELECTED_TEAM_ID_REQUIRED)
        String selectedTeamId,

        @Min(value = 1_000, message = BettingErrorCode.Message.BET_AMOUNT_TOO_LOW)
        @Max(value = 100_000, message = BettingErrorCode.Message.BET_AMOUNT_TOO_HIGH)
        long amount
) {
}
