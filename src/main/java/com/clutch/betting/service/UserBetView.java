package com.clutch.betting.service;

import com.clutch.betting.domain.UserBetStatus;

public record UserBetView(
        Long userBetId,
        Long bettingEventId,
        String selectedExternalTeamId,
        long amount,
        UserBetStatus status,
        long currentPoint
) {
}
