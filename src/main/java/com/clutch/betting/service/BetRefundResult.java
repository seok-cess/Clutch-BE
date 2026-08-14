package com.clutch.betting.service;

public record BetRefundResult(
        Long bettingEventId,
        int refundedCount,
        long totalRefundPoint,
        boolean alreadyProcessed
) {

    public static BetRefundResult alreadyProcessed(Long bettingEventId) {
        return new BetRefundResult(bettingEventId, 0, 0L, true);
    }
}
