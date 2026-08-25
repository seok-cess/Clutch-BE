package com.clutch.betting.api.request;

import com.clutch.betting.domain.UserBet;
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

        @Min(value = UserBet.MIN_AMOUNT, message = BettingErrorCode.Message.BET_AMOUNT_TOO_LOW)
        @Max(value = UserBet.MAX_AMOUNT, message = BettingErrorCode.Message.BET_AMOUNT_TOO_HIGH)
        long amount
) {
}
