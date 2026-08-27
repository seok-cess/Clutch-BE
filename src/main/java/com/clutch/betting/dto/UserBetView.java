package com.clutch.betting.dto;

import com.clutch.betting.domain.UserBet;
import com.clutch.betting.domain.UserBetStatus;

/**
 * 사용자 배팅 상세와 조회 시점 포인트를 API 계층에 전달한다.
 *
 * @param userBetId 사용자 배팅 ID
 * @param userId 배팅을 등록한 사용자 ID
 * @param bettingEventId 배팅 이벤트 ID
 * @param selectedExternalTeamId 선택한 외부 팀 ID
 * @param amount 배팅 포인트
 * @param status 사용자 배팅 상태
 * @param currentPoint 조회 시점 보유 포인트
 */
public record UserBetView(
        Long userBetId,
        Long userId,
        Long bettingEventId,
        String selectedExternalTeamId,
        long amount,
        UserBetStatus status,
        long currentPoint
) {

    /** 사용자 배팅과 조회 시점 포인트를 상세 조회 모델로 변환한다. */
    public static UserBetView from(UserBet userBet, long currentPoint) {
        return new UserBetView(
                userBet.getId(),
                userBet.getUserId(),
                userBet.getBettingEventId(),
                userBet.getSelectedExternalTeamId(),
                userBet.getAmount(),
                userBet.getStatus(),
                currentPoint
        );
    }
}
