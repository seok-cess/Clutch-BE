package com.clutch.betting.service;

import com.clutch.betting.domain.UserBetStatus;

/** 사용자 배팅 상세와 조회 시점 포인트를 API 계층에 전달한다. */
public record UserBetView(
        Long userBetId,
        Long bettingEventId,
        String selectedExternalTeamId,
        long amount,
        UserBetStatus status,
        long currentPoint
) {
}
