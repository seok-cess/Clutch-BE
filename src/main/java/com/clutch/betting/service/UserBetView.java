package com.clutch.betting.service;

import com.clutch.betting.domain.UserBetStatus;

/**
 * 사용자 배팅 상세와 조회 시점 포인트를 API 계층에 전달한다.
 *
 * @param userBetId 사용자 배팅 ID
 * @param bettingEventId 배팅 이벤트 ID
 * @param selectedExternalTeamId 선택한 외부 팀 ID
 * @param amount 배팅 포인트
 * @param status 사용자 배팅 상태
 * @param currentPoint 조회 시점 보유 포인트
 */
public record UserBetView(
        Long userBetId,
        Long bettingEventId,
        String selectedExternalTeamId,
        long amount,
        UserBetStatus status,
        long currentPoint
) {
}
