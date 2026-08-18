package com.clutch.betting.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 운영자가 확인한 세트 승자를 고착된 배팅 이벤트에 복구하는 요청이다. */
public record BettingWinnerRecoveryRequest(
        @NotBlank(message = "승리 팀 ID는 필수입니다.")
        String winnerTeamId
) {
}
