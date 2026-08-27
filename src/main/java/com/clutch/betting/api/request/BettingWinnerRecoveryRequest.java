package com.clutch.betting.api.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 운영자가 확인한 세트 승자를 결과가 누락된 종료 배팅 이벤트에 복구하는 요청이다.
 *
 * @param winnerTeamId 외부 데이터 기준 승리 팀 ID
 */
public record BettingWinnerRecoveryRequest(
        @NotBlank(message = "승리 팀 ID는 필수입니다.")
        String winnerTeamId
) {
}
