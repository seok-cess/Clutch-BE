package com.clutch.betting.exception;

/** 오류 코드를 보존해 API 계층에서 일관되게 변환할 수 있는 배팅 예외다. */
public class BettingException extends RuntimeException {

    private final BettingErrorCode errorCode;

    /** 오류 코드의 메시지를 예외 메시지로 사용한다. */
    public BettingException(BettingErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** HTTP 오류 응답으로 변환할 배팅 오류 코드를 반환한다. */
    public BettingErrorCode getErrorCode() {
        return errorCode;
    }
}
