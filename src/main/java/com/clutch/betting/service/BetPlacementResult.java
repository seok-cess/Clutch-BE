package com.clutch.betting.service;

import com.clutch.betting.domain.UserBetStatus;

/** 배팅 등록 결과와 원자적 차감 이후의 사용자 잔여 포인트다. */
public record BetPlacementResult(
        Long userBetId,
        Long bettingEventId,
        String selectedExternalTeamId,
        long amount,
        UserBetStatus status,
        long remainingPoint
) {
}
