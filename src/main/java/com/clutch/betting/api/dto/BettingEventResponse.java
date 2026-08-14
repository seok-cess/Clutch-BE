package com.clutch.betting.api.dto;

import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.service.BettingEventView;

import java.time.LocalDateTime;

public record BettingEventResponse(
        Long bettingEventId,
        String externalMatchId,
        String externalGameId,
        int setNumber,
        String firstTeamId,
        String secondTeamId,
        BettingEventStatus status,
        LocalDateTime closesAt,
        long remainingSeconds,
        boolean bettingAvailable,
        MyBetResponse myBet
) {

    public static BettingEventResponse from(BettingEventView view) {
        return new BettingEventResponse(
                view.bettingEventId(),
                view.externalMatchId(),
                view.externalGameId(),
                view.setNumber(),
                view.firstExternalTeamId(),
                view.secondExternalTeamId(),
                view.status(),
                view.closesAt(),
                view.remainingSeconds(),
                view.bettingAvailable(),
                MyBetResponse.from(view.myBet())
        );
    }

    public record MyBetResponse(
            Long userBetId,
            String selectedTeamId,
            long amount,
            UserBetStatus status
    ) {

        private static MyBetResponse from(BettingEventView.UserBetSummary summary) {
            if (summary == null) {
                return null;
            }
            return new MyBetResponse(
                    summary.userBetId(),
                    summary.selectedExternalTeamId(),
                    summary.amount(),
                    summary.status()
            );
        }
    }
}
