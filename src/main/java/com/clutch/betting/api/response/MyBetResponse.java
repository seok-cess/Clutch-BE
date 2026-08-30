package com.clutch.betting.api.response;

import com.clutch.betting.domain.BettingEventStatus;
import com.clutch.betting.domain.UserBetStatus;
import com.clutch.betting.dto.MyBetView;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 현재 사용자의 배팅 이력 한 건을 반환한다.
 *
 * @param userBetId 사용자 배팅 ID
 * @param bettingEventId 배팅 이벤트 ID
 * @param externalMatchId 외부 매치 ID
 * @param externalGameId 연결된 외부 게임 ID 또는 선개설 이벤트면 null
 * @param setNumber 매치 내 세트 번호
 * @param firstTeamId 첫 번째 배팅 선택 팀 ID
 * @param secondTeamId 두 번째 배팅 선택 팀 ID
 * @param firstTeamCode 첫 번째 배팅 선택 팀 표시 코드
 * @param secondTeamCode 두 번째 배팅 선택 팀 표시 코드
 * @param selectedTeamId 사용자가 선택한 팀 ID
 * @param amount 배팅 포인트
 * @param settlementPoint 실제 지급 또는 환불 포인트. 정산 전이면 null
 * @param netPointChange 배팅 원금 차감까지 반영한 확정 순손익. 정산 전이면 null
 * @param payoutMultiplier 현재 예상 또는 확정된 배당률. 환불 건이면 null
 * @param payoutMultiplierConfirmed 배당률이 정산 결과로 확정됐는지 여부
 * @param status 사용자 배팅 처리 상태
 * @param eventStatus 연결된 배팅 이벤트 상태
 * @param createdAt 배팅 등록 시각(UTC)
 */
public record MyBetResponse(
        Long userBetId,
        Long bettingEventId,
        String externalMatchId,
        String externalGameId,
        int setNumber,
        String firstTeamId,
        String secondTeamId,
        String firstTeamCode,
        String secondTeamCode,
        String selectedTeamId,
        long amount,
        Long settlementPoint,
        Long netPointChange,
        BigDecimal payoutMultiplier,
        boolean payoutMultiplierConfirmed,
        UserBetStatus status,
        BettingEventStatus eventStatus,
        LocalDateTime createdAt
) {

    /**
     * 서비스 조회 모델을 API 응답으로 변환한다.
     *
     * @param view 사용자 배팅 조회 모델
     * @return API 사용자 배팅 이력 응답
     */
    public static MyBetResponse from(MyBetView view) {
        return new MyBetResponse(
                view.userBetId(),
                view.bettingEventId(),
                view.externalMatchId(),
                view.externalGameId(),
                view.setNumber(),
                view.firstTeamId(),
                view.secondTeamId(),
                view.firstTeamCode(),
                view.secondTeamCode(),
                view.selectedTeamId(),
                view.amount(),
                view.settlementPoint(),
                view.netPointChange(),
                view.payoutMultiplier(),
                view.payoutMultiplierConfirmed(),
                view.status(),
                view.eventStatus(),
                view.createdAt()
        );
    }
}
