package com.clutch.watch.exception;

import org.springframework.http.HttpStatus;

/**
 * 시청 보상 기능에서 사용하는 오류 코드와 HTTP 응답 정책.
 */
public enum WatchError {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    MATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "경기를 찾을 수 없습니다."),
    MATCH_NOT_WATCHABLE(HttpStatus.CONFLICT, "현재 시청 가능한 경기가 아닙니다."),
    WATCH_SESSION_SWITCHING(HttpStatus.CONFLICT, "시청 세션 전환이 진행 중입니다."),
    WATCH_SESSION_REPLACED(HttpStatus.CONFLICT, "다른 시청 세션으로 교체되었습니다."),
    WATCH_SESSION_EXPIRED(HttpStatus.GONE, "시청 세션이 만료되었습니다. 새로 입장해 주세요."),
    WATCH_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "시청 세션을 찾을 수 없습니다."),
    WATCH_SESSION_USER_MISMATCH(HttpStatus.FORBIDDEN, "다른 사용자의 시청 세션입니다."),
    INVALID_HEARTBEAT_SEQUENCE(HttpStatus.CONFLICT, "Heartbeat 순번이 올바르지 않습니다."),

    SESSION_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "세션 키는 필수입니다."),
    USER_ID_REQUIRED(HttpStatus.BAD_REQUEST, "사용자 ID는 필수입니다."),
    MATCH_ID_REQUIRED(HttpStatus.BAD_REQUEST, "경기 ID는 필수입니다."),
    WATCH_SESSION_ID_REQUIRED(HttpStatus.BAD_REQUEST, "시청 세션 ID는 필수입니다."),
    ENTERED_AT_REQUIRED(HttpStatus.BAD_REQUEST, "입장 시각은 필수입니다."),
    LAST_SEEN_AT_REQUIRED(HttpStatus.BAD_REQUEST, "마지막 확인 시각은 필수입니다."),
    WATCH_SESSION_ALREADY_COMPLETED(HttpStatus.CONFLICT, "이미 완료된 시청 세션입니다."),
    LAST_SEEN_BEFORE_ENTERED_AT(HttpStatus.BAD_REQUEST, "마지막 확인 시각은 입장 시각보다 이전일 수 없습니다."),
    ELIGIBLE_TIME_NEGATIVE(HttpStatus.BAD_REQUEST, "유효 시청시간은 음수일 수 없습니다."),
    AWARDED_POINT_NEGATIVE(HttpStatus.BAD_REQUEST, "지급 포인트는 음수일 수 없습니다."),

    WATCH_SESSION_STATE_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "기존 활성 시청 세션 상태를 찾을 수 없습니다."),
    REDIS_SESSION_FIELD_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "Redis 시청 세션의 필수 필드가 없습니다."),
    REDIS_SESSION_FIELD_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "Redis 시청 세션 필드 형식이 올바르지 않습니다."),
    HEARTBEAT_RESULT_UNKNOWN(HttpStatus.INTERNAL_SERVER_ERROR, "알 수 없는 Heartbeat 처리 결과입니다."),
    HEARTBEAT_RESULT_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "Heartbeat Lua 스크립트 결과가 없습니다."),
    HEARTBEAT_SUCCESS_MAPPING(HttpStatus.INTERNAL_SERVER_ERROR, "성공한 Heartbeat 결과는 오류로 변환할 수 없습니다."),
    REWARD_SNAPSHOT_REQUIRED(HttpStatus.INTERNAL_SERVER_ERROR, "포인트 지급에 필요한 Redis 시청 세션이 없습니다."),
    REDIS_SESSION_USER_MISMATCH(HttpStatus.INTERNAL_SERVER_ERROR, "Redis 세션의 사용자 ID가 DB 세션과 일치하지 않습니다."),
    REDIS_SESSION_MATCH_MISMATCH(HttpStatus.INTERNAL_SERVER_ERROR, "Redis 세션의 경기 ID가 DB 세션과 일치하지 않습니다."),
    REDIS_SESSION_TIME_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "Redis 시청 세션 시각이 올바르지 않습니다."),
    POINT_TRANSACTION_NOT_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "완료된 시청 세션의 포인트 거래를 찾을 수 없습니다."),
    REWARD_POINT_OVERFLOW(HttpStatus.INTERNAL_SERVER_ERROR, "지급 포인트 계산 범위를 초과했습니다."),
    USER_POINT_OVERFLOW(HttpStatus.INTERNAL_SERVER_ERROR, "사용자 포인트 계산 범위를 초과했습니다."),

    ALIVE_TTL_NOT_LONGER_THAN_HEARTBEAT(HttpStatus.INTERNAL_SERVER_ERROR, "Alive TTL은 heartbeat 주기보다 길어야 합니다."),
    ACTIVE_TTL_NOT_LONGER_THAN_ALIVE(HttpStatus.INTERNAL_SERVER_ERROR, "Active TTL은 Alive TTL보다 길어야 합니다."),
    SESSION_TTL_NOT_LONGER_THAN_ACTIVE(HttpStatus.INTERNAL_SERVER_ERROR, "Session TTL은 Active TTL보다 길어야 합니다.");

    private final HttpStatus status;
    private final String message;

    WatchError(HttpStatus status, String message) {
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
     * 사용자에게 전달하거나 로그에서 확인할 한국어 오류 메시지를 반환한다.
     *
     * @return 오류 메시지
     */
    public String message() {
        return message;
    }
}
