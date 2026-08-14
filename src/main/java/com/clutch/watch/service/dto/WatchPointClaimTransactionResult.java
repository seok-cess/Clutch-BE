package com.clutch.watch.service.dto;

/**
 * DB에서 확정된 한 회차의 포인트 지급 결과.
 */
public record WatchPointClaimTransactionResult(
        long rewardSequence,
        long awardedPoint,
        long totalPoint
) {
}
