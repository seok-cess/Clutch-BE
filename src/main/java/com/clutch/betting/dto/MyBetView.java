package com.clutch.betting.dto;

import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.domain.UserBetStatus;

import java.time.LocalDateTime;

/** 현재 사용자의 배팅과 연결된 경기·세트 정보를 전달한다. */
public record MyBetView(
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
}
