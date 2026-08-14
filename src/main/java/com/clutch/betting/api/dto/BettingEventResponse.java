package com.clutch.betting.api.dto;

import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.service.BettingEventView;

import java.time.LocalDateTime;

/**
 * 현재 세트 배팅 이벤트의 상태와 사용자 참여 정보를 반환한다.
 *
 * @param bettingEventId 배팅 이벤트 ID
 * @param externalMatchId 외부 매치 ID
 * @param externalGameId 연결된 외부 게임 ID
 * @param setNumber 세트 번호
 * @param firstTeamId 첫 번째 선택 팀 ID
 * @param secondTeamId 두 번째 선택 팀 ID
 * @param status 배팅 이벤트 상태
 * @param closesAt 배팅 마감 시각
 * @param remainingSeconds 마감까지 남은 초
 * @param bettingAvailable 현재 사용자의 배팅 가능 여부
 * @param myBet 현재 사용자의 배팅 요약
 */
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

    /**
     * 조회 서비스의 이벤트 뷰를 API 응답으로 변환한다.
     *
     * @param view 현재 배팅 이벤트 조회 모델
     * @return API 배팅 이벤트 응답
     */
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

    /**
     * 현재 이벤트에 대한 사용자의 배팅 요약이다.
     *
     * @param userBetId 사용자 배팅 ID
     * @param selectedTeamId 사용자가 선택한 외부 팀 ID
     * @param amount 배팅 포인트
     * @param status 사용자 배팅 상태
     */
    public record MyBetResponse(
            Long userBetId,
            String selectedTeamId,
            long amount,
            UserBetStatus status
    ) {

        /**
         * 배팅 요약이 없으면 null을 유지하고, 있으면 응답 DTO로 변환한다.
         *
         * @param summary 사용자 배팅 요약 조회 모델
         * @return 변환된 배팅 요약 또는 배팅이 없을 때 null
         */
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
