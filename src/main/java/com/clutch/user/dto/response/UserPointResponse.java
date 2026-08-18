package com.clutch.user.dto.response;

/**
 * 현재 사용자의 보유 포인트를 반환한다.
 *
 * @param userId 사용자 ID
 * @param point 조회 시점의 보유 포인트
 */
public record UserPointResponse(
        Long userId,
        long point
) {
}
