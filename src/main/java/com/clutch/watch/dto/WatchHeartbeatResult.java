package com.clutch.watch.dto;

/**
 * Heartbeat 처리 후 API 계층에 전달할 현재 회차의 보상 상태.
 *
 * @param rewardState 현재 회차의 포인트 누적 상태
 * @param rewardSequence 현재 포인트 회차
 * @param accumulatedSeconds 현재 회차의 누적 시청 시간(초)
 * @param remainingSeconds 포인트 수령까지 남은 시간(초)
 * @param rewardPoint 한 회차에서 수령할 포인트
 */
public record WatchHeartbeatResult(
        WatchRewardState rewardState,
        long rewardSequence,
        long accumulatedSeconds,
        long remainingSeconds,
        long rewardPoint
) {
}
