package com.clutch.watch.exception;

import java.util.Objects;

/**
 * 시청 세션 처리 실패를 API 오류 정책과 함께 전달하는 예외.
 */
public class WatchSessionException extends RuntimeException {

    private final WatchSessionError error;

    /**
     * 지정한 시청 세션 오류로 예외를 생성한다.
     *
     * @param error 발생한 시청 세션 오류
     */
    public WatchSessionException(WatchSessionError error) {
        super(Objects.requireNonNull(error, "시청 세션 오류는 필수입니다.").message());
        this.error = error;
    }

    /**
     * 발생한 시청 세션 오류를 반환한다.
     *
     * @return HTTP 상태와 오류 코드를 가진 시청 세션 오류
     */
    public WatchSessionError getError() {
        return error;
    }
}
