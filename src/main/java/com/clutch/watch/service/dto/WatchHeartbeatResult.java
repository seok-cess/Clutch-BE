package com.clutch.watch.service.dto;

/**
 * Heartbeat 처리 후 프론트엔드에 전달할 현재 회차의 보상 상태.
 */
public record WatchHeartbeatResult(
        WatchRewardState rewardState,
        long rewardSequence,
        long accumulatedSeconds,
        long remainingSeconds,
        long rewardPoint
) {
}
