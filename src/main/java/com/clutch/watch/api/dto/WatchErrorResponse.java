package com.clutch.watch.api.dto;

/**
 * 시청 세션 API 오류 응답.
 *
 * @param code 클라이언트가 분기 처리할 오류 코드
 * @param message 사용자에게 전달할 한국어 오류 메시지
 */
public record WatchErrorResponse(
        String code,
        String message
) {
}
