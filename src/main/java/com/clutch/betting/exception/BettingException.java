package com.clutch.betting.exception;

public class BettingException extends RuntimeException {

    private final BettingErrorCode errorCode;

    public BettingException(BettingErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BettingErrorCode getErrorCode() {
        return errorCode;
    }
}
