package com.clutch.betting.dto.response;

import com.clutch.betting.exception.BettingErrorCode;

/**
 * 배팅 API의 오류 코드와 사용자 메시지를 담는다.
 *
 * @param code 오류 식별 코드
 * @param message 사용자 오류 메시지
 */
public record BettingErrorResponse(
        BettingErrorCode code,
        String message
) {
}
