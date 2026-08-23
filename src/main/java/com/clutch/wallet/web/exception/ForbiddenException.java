package com.clutch.wallet.web.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 관리자 권한이 없는 사용자가 관리자 전용 기능에 접근할 때 발생하는 예외. */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException{
    public ForbiddenException(){
        super("관리자 권한이 필요합니다.");
    }
}
