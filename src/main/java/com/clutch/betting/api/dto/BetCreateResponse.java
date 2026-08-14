package com.clutch.betting.api.dto;

import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.service.BetPlacementResult;

/** 등록된 사용자 배팅과 차감 후 잔여 포인트를 반환한다. */
public record BetCreateResponse(
        Long userBetId,
        Long bettingEventId,
        String selectedTeamId,
        long amount,
        UserBetStatus status,
        long remainingPoint
) {

    /** 서비스 결과를 외부 API 응답 계약으로 변환한다. */
    public static BetCreateResponse from(BetPlacementResult result) {
        return new BetCreateResponse(
                result.userBetId(),
                result.bettingEventId(),
                result.selectedExternalTeamId(),
                result.amount(),
                result.status(),
                result.remainingPoint()
        );
    }
}
