package com.clutch.watch.service.dto;

/**
 * 포인트 수령 완료 후 클라이언트에 전달할 결과.
 */
public record WatchPointClaimResult(
        long rewardSequence,
        long awardedPoint,
        long totalPoint,
        long nextRewardSequence
) {
}
