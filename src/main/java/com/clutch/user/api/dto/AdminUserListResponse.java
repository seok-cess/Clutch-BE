package com.clutch.user.api.dto;

import java.util.List;

/**
 * 커서 기반 회원 목록 조회 결과.
 *
 * @param users 현재 페이지의 회원 목록
 * @param nextCursor 다음 페이지 조회에 쓸 마지막 회원 ID, 마지막 페이지면 null
 * @param hasNext 다음 페이지 존재 여부
 */
public record AdminUserListResponse(
        List<AdminUserSummaryResponse> users,
        Long nextCursor,
        boolean hasNext
) {
}
