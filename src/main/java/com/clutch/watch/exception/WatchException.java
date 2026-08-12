package com.clutch.watch.exception;

/**
 * 시청 보상 처리 실패를 오류 코드와 함께 전달하는 공통 예외.
 */
public class WatchException extends RuntimeException {

    private final WatchError error;

    /**
     * 지정한 시청 보상 오류로 예외를 생성한다.
     *
     * @param error 발생한 시청 보상 오류
     */
    public WatchException(WatchError error) {
        super(error.message());
        this.error = error;
    }

    /**
     * 지정한 시청 보상 오류와 원인 예외로 예외를 생성한다.
     *
     * @param error 발생한 시청 보상 오류
     * @param cause 오류를 발생시킨 원인 예외
     */
    public WatchException(WatchError error, Throwable cause) {
        super(error.message(), cause);
        this.error = error;
    }

    /**
     * 발생한 시청 보상 오류를 반환한다.
     *
     * @return HTTP 상태와 오류 코드를 가진 시청 보상 오류
     */
    public WatchError getError() {
        return error;
    }
}
