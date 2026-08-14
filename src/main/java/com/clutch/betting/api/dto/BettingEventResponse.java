package com.clutch.betting.api.dto;

import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.service.BettingEventView;

import java.time.LocalDateTime;

/** 현재 세트 배팅 이벤트의 상태와 사용자 참여 정보를 반환한다. */
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

    /** 조회 서비스의 이벤트 뷰를 API 응답으로 변환한다. */
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

    /** 현재 이벤트에 대한 사용자의 배팅 요약이다. */
    public record MyBetResponse(
            Long userBetId,
            String selectedTeamId,
            long amount,
            UserBetStatus status
    ) {

        /** 배팅 요약이 없으면 null을 유지하고, 있으면 응답 DTO로 변환한다. */
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
