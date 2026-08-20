package com.clutch.lolesports.source;

/** STUB 소스로 전환하기 전에 replay 서버 상태를 확인하지 못했을 때 발생한다. */
public class ExternalSourceSwitchException extends RuntimeException {

    public ExternalSourceSwitchException(String message, Throwable cause) {
        super(message, cause);
    }
}
