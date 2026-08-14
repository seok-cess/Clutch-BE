package com.clutch.betting.service;

/** 이벤트 환불 건수·총액과 기존 처리 여부를 전달한다. */
public record BetRefundResult(
        Long bettingEventId,
        int refundedCount,
        long totalRefundPoint,
        boolean alreadyProcessed
) {

    /** 환불할 등록 배팅이 없는 경우의 멱등 결과를 생성한다. */
    public static BetRefundResult alreadyProcessed(Long bettingEventId) {
        return new BetRefundResult(bettingEventId, 0, 0L, true);
    }
}
