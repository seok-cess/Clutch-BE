package com.clutch.betting.service.placement;

import com.clutch.betting.domain.UserBetStatus;

/**
 * 배팅 등록 결과와 원자적 차감 이후의 사용자 잔여 포인트다.
 *
 * @param userBetId 사용자 배팅 ID
 * @param bettingEventId 배팅 이벤트 ID
 * @param selectedExternalTeamId 선택한 외부 팀 ID
 * @param amount 배팅 포인트
 * @param status 사용자 배팅 상태
 * @param remainingPoint 차감 후 잔여 포인트
 */
public record BetPlacementResult(
        Long userBetId,
        Long bettingEventId,
        String selectedExternalTeamId,
        long amount,
        UserBetStatus status,
        long remainingPoint
) {
}
