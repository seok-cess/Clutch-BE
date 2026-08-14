package com.clutch.betting.dto.response;

import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.dto.BetPlacementResult;

/**
 * 등록된 사용자 배팅과 차감 후 잔여 포인트를 반환한다.
 *
 * @param userBetId 생성된 사용자 배팅 ID
 * @param userId 배팅을 등록한 사용자 ID
 * @param bettingEventId 배팅 이벤트 ID
 * @param selectedTeamId 사용자가 선택한 외부 팀 ID
 * @param amount 배팅 포인트
 * @param status 사용자 배팅 상태
 * @param remainingPoint 차감 후 잔여 포인트
 */
public record BetCreateResponse(
        Long userBetId,
        Long userId,
        Long bettingEventId,
        String selectedTeamId,
        long amount,
        UserBetStatus status,
        long remainingPoint
) {

    /**
     * 서비스 결과를 외부 API 응답 계약으로 변환한다.
     *
     * @param result 배팅 등록 서비스 결과
     * @return API 배팅 등록 응답
     */
    public static BetCreateResponse from(BetPlacementResult result) {
        return new BetCreateResponse(
                result.userBetId(),
                result.userId(),
                result.bettingEventId(),
                result.selectedExternalTeamId(),
                result.amount(),
                result.status(),
                result.remainingPoint()
        );
    }
}
