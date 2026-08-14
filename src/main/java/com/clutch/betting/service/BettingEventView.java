package com.clutch.betting.service;

import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.domain.UserBetStatus;

import java.time.LocalDateTime;

public record BettingEventView(
        Long bettingEventId,
        String externalMatchId,
        String externalGameId,
        int setNumber,
        String firstExternalTeamId,
        String secondExternalTeamId,
        BettingEventStatus status,
        LocalDateTime closesAt,
        long remainingSeconds,
        boolean bettingAvailable,
        UserBetSummary myBet
) {

    public record UserBetSummary(
            Long userBetId,
            String selectedExternalTeamId,
            long amount,
            UserBetStatus status
    ) {
    }
}
