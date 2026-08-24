package com.clutch.replay.service;

/** 실제 외부 API 모드에서 재생 조작을 시도했을 때 발생한다. */
public class ReplaySourceModeException extends ReplayControlException {

    public ReplaySourceModeException() {
        super("STUB 소스 모드에서만 test 경기를 시작할 수 있다");
    }
}
