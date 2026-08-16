package com.clutch.watch.dto.response;

import com.clutch.watch.dto.WatchHeartbeatResult;
import com.clutch.watch.dto.WatchRewardState;

/**
 * Heartbeat 처리 후 현재 포인트 수령 상태 응답.
 *
 * @param rewardState 현재 회차의 포인트 누적 상태
 * @param rewardSequence 현재 포인트 회차
 * @param accumulatedSeconds 현재 회차의 누적 시청 시간(초)
 * @param remainingSeconds 포인트 수령까지 남은 시간(초)
 * @param rewardPoint 한 회차에서 수령할 포인트
 */
public record HeartbeatResponse(
        WatchRewardState rewardState,
        long rewardSequence,
        long accumulatedSeconds,
        long remainingSeconds,
        long rewardPoint
) {

    /**
     * 서비스 Heartbeat 결과를 API 응답으로 변환한다.
     *
     * @param result 서비스 Heartbeat 처리 결과
     * @return 클라이언트에 반환할 Heartbeat 응답
     */
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
