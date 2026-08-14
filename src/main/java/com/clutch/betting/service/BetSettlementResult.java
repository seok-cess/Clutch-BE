package com.clutch.betting.service;

/** 이벤트 정산의 적중·실패 건수와 총 지급 포인트를 전달한다. */
public record BetSettlementResult(
        Long bettingEventId,
        int wonCount,
        int lostCount,
        long totalPayoutPoint,
        boolean alreadyProcessed
) {

    /** 이미 정산된 이벤트에 대한 멱등 결과를 생성한다. */
    public static BetSettlementResult alreadyProcessed(Long bettingEventId) {
        return new BetSettlementResult(bettingEventId, 0, 0, 0L, true);
    }
}
