package com.clutch.betting.api.dto;

import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.service.UserBetView;

public record UserBetResponse(
        Long userBetId,
        Long bettingEventId,
        String selectedTeamId,
        long amount,
        UserBetStatus status,
        long currentPoint
) {

    public static UserBetResponse from(UserBetView view) {
        return new UserBetResponse(
                view.userBetId(),
                view.bettingEventId(),
                view.selectedExternalTeamId(),
                view.amount(),
                view.status(),
                view.currentPoint()
        );
    }
}
