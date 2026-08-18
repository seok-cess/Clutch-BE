package com.clutch.betting.dto.response;

import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.dto.MyBetView;

import java.time.LocalDateTime;

/** 현재 사용자의 배팅 이력 한 건을 반환한다. */
public record MyBetResponse(
        Long userBetId,
        Long bettingEventId,
        String externalMatchId,
        String externalGameId,
        int setNumber,
        String firstTeamId,
        String secondTeamId,
        String selectedTeamId,
        long amount,
        UserBetStatus status,
        BettingEventStatus eventStatus,
        LocalDateTime createdAt
) {

    /** 서비스 조회 모델을 API 응답으로 변환한다. */
    public static MyBetResponse from(MyBetView view) {
        return new MyBetResponse(
                view.userBetId(),
                view.bettingEventId(),
                view.externalMatchId(),
                view.externalGameId(),
                view.setNumber(),
                view.firstTeamId(),
                view.secondTeamId(),
                view.selectedTeamId(),
                view.amount(),
                view.status(),
                view.eventStatus(),
                view.createdAt()
        );
    }
}
