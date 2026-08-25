package com.clutch.betting.dto;

import com.clutch.betting.domain.UserBet;
import com.clutch.betting.domain.UserBetStatus;

/**
 * 배팅 등록 결과와 원자적 차감 이후의 사용자 잔여 포인트다.
 *
 * @param userBetId 사용자 배팅 ID
 * @param userId 배팅을 등록한 사용자 ID
 * @param bettingEventId 배팅 이벤트 ID
 * @param selectedExternalTeamId 선택한 외부 팀 ID
 * @param amount 배팅 포인트
 * @param status 사용자 배팅 상태
 * @param remainingPoint 차감 후 잔여 포인트
 */
public record BetPlacementResult(
        Long userBetId,
        Long userId,
        Long bettingEventId,
        String selectedExternalTeamId,
        long amount,
        UserBetStatus status,
        long remainingPoint
) {

    /** 등록된 사용자 배팅과 포인트 차감 후 잔액을 서비스 결과로 묶는다. */
    public static BetPlacementResult from(UserBet userBet, long remainingPoint) {
        return new BetPlacementResult(
                userBet.getId(),
                userBet.getUserId(),
                userBet.getBettingEventId(),
                userBet.getSelectedExternalTeamId(),
                userBet.getAmount(),
                userBet.getStatus(),
                remainingPoint
        );
    }
}
