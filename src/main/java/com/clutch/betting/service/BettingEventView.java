package com.clutch.betting.service;

import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.domain.UserBetStatus;

import java.time.LocalDateTime;

/** 현재 배팅 이벤트와 사용자 참여 여부를 API 계층에 전달하는 조회 모델이다. */
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

    /** 현재 이벤트에 등록된 사용자 배팅의 최소 조회 정보다. */
    public record UserBetSummary(
            Long userBetId,
            String selectedExternalTeamId,
            long amount,
            UserBetStatus status
    ) {
    }
}
