package com.clutch.watch.redis.heartbeat;

import com.clutch.watch.exception.WatchError;
import com.clutch.watch.exception.WatchException;

/**
 * Heartbeat Lua 스크립트의 처리 상태와 성공 시점의 보상 누적 상태.
 */
public record HeartbeatProcessingResult(
        HeartbeatResult status,
        long eligibleMilliseconds,
        long rewardSequence
) {

    private static final String SUCCESS_PREFIX = "SUCCESS:";

    public static HeartbeatProcessingResult from(String value) {
        if (!value.startsWith(SUCCESS_PREFIX)) {
            return failure(HeartbeatResult.from(value));
        }

        String[] fields = value.split(":", -1);
        if (fields.length != 3) {
            throw new WatchException(WatchError.HEARTBEAT_RESULT_UNKNOWN);
        }

        try {
            return new HeartbeatProcessingResult(
                    HeartbeatResult.SUCCESS,
                    Long.parseLong(fields[1]),
                    Long.parseLong(fields[2])
            );
        } catch (NumberFormatException exception) {
            throw new WatchException(WatchError.HEARTBEAT_RESULT_UNKNOWN, exception);
        }
    }

    public static HeartbeatProcessingResult failure(HeartbeatResult status) {
        if (status == HeartbeatResult.SUCCESS) {
            throw new WatchException(WatchError.HEARTBEAT_SUCCESS_MAPPING);
        }
        return new HeartbeatProcessingResult(status, 0L, 0L);
    }
}
