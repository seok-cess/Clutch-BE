package com.clutch.betting.dto;

import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.domain.UserBetStatus;

/**
 * 현재 배팅 이벤트와 사용자 참여 여부를 API 계층에 전달하는 조회 모델이다.
 *
 * @param bettingEventId 배팅 이벤트 ID
 * @param externalMatchId 외부 매치 ID
 * @param externalGameId 연결된 외부 게임 ID
 * @param setNumber 세트 번호
 * @param firstExternalTeamId 첫 번째 참가 팀 ID
 * @param secondExternalTeamId 두 번째 참가 팀 ID
 * @param status 배팅 이벤트 상태
 * @param bettingAvailable 현재 사용자의 배팅 가능 여부
 * @param myBet 현재 사용자의 배팅 요약
 */
public record BettingEventView(
        Long bettingEventId,
        String externalMatchId,
        String externalGameId,
        int setNumber,
        String firstExternalTeamId,
        String secondExternalTeamId,
        BettingEventStatus status,
        boolean bettingAvailable,
        UserBetSummary myBet
) {

    /**
     * 현재 이벤트에 등록된 사용자 배팅의 최소 조회 정보다.
     *
     * @param userBetId 사용자 배팅 ID
     * @param selectedExternalTeamId 선택한 외부 팀 ID
     * @param amount 배팅 포인트
     * @param status 사용자 배팅 상태
     */
    public record UserBetSummary(
            Long userBetId,
            String selectedExternalTeamId,
            long amount,
            UserBetStatus status
    ) {
    }
}
