package com.clutch.wallet.web.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class ForbiddenException extends RuntimeException{
    public ForbiddenException(){
        super("관리자 권한이 필요합니다.");
    }
}
