package com.clutch.betting.service;

import com.clutch.betting.domain.UserBetStatus;

public record BetPlacementResult(
        Long userBetId,
        Long bettingEventId,
        String selectedExternalTeamId,
        long amount,
        UserBetStatus status
) {
}
