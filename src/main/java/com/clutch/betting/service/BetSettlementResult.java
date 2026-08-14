package com.clutch.betting.service;

/**
 * 이벤트 정산의 적중·실패 건수와 총 지급 포인트를 전달한다.
 *
 * @param bettingEventId 배팅 이벤트 ID
 * @param wonCount 적중 배팅 수
 * @param lostCount 실패 배팅 수
 * @param totalPayoutPoint 총 지급 포인트
 * @param alreadyProcessed 기존 처리 완료 여부
 */
public record BetSettlementResult(
        Long bettingEventId,
        int wonCount,
        int lostCount,
        long totalPayoutPoint,
        boolean alreadyProcessed
) {

    /**
     * 이미 정산된 이벤트에 대한 멱등 결과를 생성한다.
     *
     * @param bettingEventId 배팅 이벤트 ID
     * @return 기존 처리 완료를 나타내는 정산 결과
     */
    public static BetSettlementResult alreadyProcessed(Long bettingEventId) {
        return new BetSettlementResult(bettingEventId, 0, 0, 0L, true);
    }
}
