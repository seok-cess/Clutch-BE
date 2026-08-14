package com.clutch.betting.api.dto;

/** 배팅 API의 오류 코드와 사용자 메시지를 담는다. */
public record BettingErrorResponse(
        String code,
        String message
) {
}
