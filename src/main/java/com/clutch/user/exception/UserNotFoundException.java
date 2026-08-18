package com.clutch.user.exception;

/** 요청한 사용자가 존재하지 않을 때 발생한다. */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super("사용자를 찾을 수 없습니다.");
    }
}
