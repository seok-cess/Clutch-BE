package com.clutch.betting.dto.response;

import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.dto.UserBetView;

/**
 * 사용자 배팅 상세와 현재 포인트를 반환한다.
 *
 * @param userBetId 사용자 배팅 ID
 * @param userId 배팅을 등록한 사용자 ID
 * @param bettingEventId 배팅 이벤트 ID
 * @param selectedTeamId 사용자가 선택한 외부 팀 ID
 * @param amount 배팅 포인트
 * @param status 사용자 배팅 상태
 * @param currentPoint 조회 시점 보유 포인트
 */
public record UserBetResponse(
        Long userBetId,
        Long userId,
        Long bettingEventId,
        String selectedTeamId,
        long amount,
        UserBetStatus status,
        long currentPoint
) {

    /**
     * 서비스 조회 결과를 사용자 배팅 응답으로 변환한다.
     *
     * @param view 사용자 배팅 조회 모델
     * @return API 사용자 배팅 응답
     */
    public static UserBetResponse from(UserBetView view) {
        return new UserBetResponse(
                view.userBetId(),
                view.userId(),
                view.bettingEventId(),
                view.selectedExternalTeamId(),
                view.amount(),
                view.status(),
                view.currentPoint()
        );
    }
}
