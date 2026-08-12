package com.clutch.watch.exception;

import org.springframework.http.HttpStatus;

/**
 * 시청 세션 API에서 사용하는 오류 코드와 HTTP 응답 정책.
 */
public enum WatchSessionError {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    MATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "경기를 찾을 수 없습니다."),
    MATCH_NOT_WATCHABLE(HttpStatus.CONFLICT, "현재 시청 가능한 경기가 아닙니다."),
    WATCH_SESSION_SWITCHING(HttpStatus.CONFLICT, "시청 세션 전환이 진행 중입니다."),
    WATCH_SESSION_REPLACED(HttpStatus.CONFLICT, "다른 시청 세션으로 교체되었습니다."),
    WATCH_SESSION_EXPIRED(HttpStatus.GONE, "시청 세션이 만료되었습니다. 새로 입장해 주세요."),
    WATCH_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "시청 세션을 찾을 수 없습니다."),
    WATCH_SESSION_USER_MISMATCH(HttpStatus.FORBIDDEN, "다른 사용자의 시청 세션입니다."),
    INVALID_HEARTBEAT_SEQUENCE(HttpStatus.CONFLICT, "Heartbeat 순번이 올바르지 않습니다."),
    WATCH_SESSION_STATE_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "기존 활성 시청 세션 상태를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    WatchSessionError(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * 오류에 대응하는 HTTP 상태를 반환한다.
     *
     * @return HTTP 응답 상태
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * 사용자에게 전달할 한국어 오류 메시지를 반환한다.
     *
     * @return 오류 메시지
     */
    public String message() {
        return message;
    }
}
