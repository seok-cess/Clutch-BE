package com.clutch.betting.service;

public record BetSettlementResult(
        Long bettingEventId,
        int wonCount,
        int lostCount,
        long totalPayoutPoint,
        boolean alreadyProcessed
) {

    public static BetSettlementResult alreadyProcessed(Long bettingEventId) {
        return new BetSettlementResult(bettingEventId, 0, 0, 0L, true);
    }
}
