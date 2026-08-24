package com.clutch.wallet.web.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** X-User-Id 요청 헤더가 없거나 유효하지 않을 때 발생하는 예외. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class MissingUserIdHeaderException extends RuntimeException{
    public MissingUserIdHeaderException(){
        super("X-User-Id 헤더가 필요합니다.");
    }
}
