package com.clutch.watch.api.dto;

import com.clutch.watch.service.dto.WatchPointClaimResult;

/**
 * 시청 포인트 수령 완료 응답.
 */
public record WatchPointClaimResponse(
        long rewardSequence,
        long awardedPoint,
        long totalPoint,
        long nextRewardSequence
) {

    public static WatchPointClaimResponse from(WatchPointClaimResult result) {
        return new WatchPointClaimResponse(
                result.rewardSequence(),
                result.awardedPoint(),
                result.totalPoint(),
                result.nextRewardSequence()
        );
    }
}
