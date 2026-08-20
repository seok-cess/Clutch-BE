package com.clutch.replay.service;

/** replay 스텁 서버를 제어할 수 없을 때 발생한다. */
public class ReplayControlException extends RuntimeException {

    public ReplayControlException(String message, Throwable cause) {
        super(message, cause);
    }

    public ReplayControlException(String message) {
        super(message);
    }
}
