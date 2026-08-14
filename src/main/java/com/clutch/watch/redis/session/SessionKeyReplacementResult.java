package com.clutch.watch.redis.session;

import com.clutch.watch.exception.WatchError;
import com.clutch.watch.exception.WatchException;

/**
 * 동일 경기 재입장 시 Redis sessionKey 교체 결과.
 */
public enum SessionKeyReplacementResult {
    SUCCESS,
    REPLACED,
    EXPIRED,
    SESSION_NOT_FOUND,
    USER_MISMATCH,
    SESSION_KEY_CONFLICT;

    static SessionKeyReplacementResult from(String value) {
        try {
            return SessionKeyReplacementResult.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new WatchException(WatchError.SESSION_KEY_REPLACEMENT_RESULT_UNKNOWN, exception);
        }
    }
}
