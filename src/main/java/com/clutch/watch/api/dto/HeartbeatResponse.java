package com.clutch.watch.api.dto;

import com.clutch.watch.service.dto.WatchHeartbeatResult;
import com.clutch.watch.service.dto.WatchRewardState;

/**
 * Heartbeat 처리 후 현재 포인트 수령 상태 응답.
 */
public record HeartbeatResponse(
        WatchRewardState rewardState,
        long rewardSequence,
        long accumulatedSeconds,
        long remainingSeconds,
        long rewardPoint
) {

    public static HeartbeatResponse from(WatchHeartbeatResult result) {
        return new HeartbeatResponse(
                result.rewardState(),
                result.rewardSequence(),
                result.accumulatedSeconds(),
                result.remainingSeconds(),
                result.rewardPoint()
        );
    }
}
