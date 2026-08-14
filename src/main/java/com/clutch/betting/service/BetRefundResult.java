package com.clutch.betting.service;

/**
 * 이벤트 환불 건수·총액과 기존 처리 여부를 전달한다.
 *
 * @param bettingEventId 배팅 이벤트 ID
 * @param refundedCount 환불된 사용자 배팅 수
 * @param totalRefundPoint 총 환불 포인트
 * @param alreadyProcessed 기존 처리 완료 여부
 */
public record BetRefundResult(
        Long bettingEventId,
        int refundedCount,
        long totalRefundPoint,
        boolean alreadyProcessed
) {

    /**
     * 환불할 등록 배팅이 없는 경우의 멱등 결과를 생성한다.
     *
     * @param bettingEventId 배팅 이벤트 ID
     * @return 기존 처리 완료를 나타내는 환불 결과
     */
    public static BetRefundResult alreadyProcessed(Long bettingEventId) {
        return new BetRefundResult(bettingEventId, 0, 0L, true);
    }
}
