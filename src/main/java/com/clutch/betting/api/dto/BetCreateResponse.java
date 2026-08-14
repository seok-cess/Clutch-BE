package com.clutch.betting.api.dto;

import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.service.BetPlacementResult;

public record BetCreateResponse(
        Long userBetId,
        Long bettingEventId,
        String selectedTeamId,
        long amount,
        UserBetStatus status,
        long remainingPoint
) {

    public static BetCreateResponse from(BetPlacementResult result) {
        return new BetCreateResponse(
                result.userBetId(),
                result.bettingEventId(),
                result.selectedExternalTeamId(),
                result.amount(),
                result.status(),
                result.remainingPoint()
        );
    }
}
