package com.clutch.user.dto.response;

/**
 * 사용자 조회 API 오류를 반환한다.
 *
 * @param code 오류 식별 코드
 * @param message 사용자 메시지
 */
public record UserErrorResponse(
        String code,
        String message
) {
}
