package com.clutch.watch.redis.heartbeat;

import com.clutch.watch.exception.WatchError;
import com.clutch.watch.exception.WatchException;

/**
 * Heartbeat Lua 스크립트 처리 결과.
 */
public enum HeartbeatResult {
    SUCCESS,
    SWITCHING,
    REPLACED,
    EXPIRED,
    SESSION_NOT_FOUND,
    USER_MISMATCH,
    INVALID_SEQUENCE;

    /**
     * Lua 스크립트가 반환한 문자열을 heartbeat 처리 결과로 변환한다.
     *
     * @param value Lua 스크립트가 반환한 결과 문자열
     * @return 문자열에 대응하는 heartbeat 처리 결과
     * @throws WatchException 정의되지 않은 결과 문자열인 경우
     */
    public static HeartbeatResult from(String value) {
        try {
            return HeartbeatResult.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new WatchException(WatchError.HEARTBEAT_RESULT_UNKNOWN, exception);
        }
    }
}
