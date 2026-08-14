package com.clutch.betting.api.dto;

import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.service.UserBetView;

/** 사용자 배팅 상세와 현재 포인트를 반환한다. */
public record UserBetResponse(
        Long userBetId,
        Long bettingEventId,
        String selectedTeamId,
        long amount,
        UserBetStatus status,
        long currentPoint
) {

    /** 서비스 조회 결과를 사용자 배팅 응답으로 변환한다. */
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
