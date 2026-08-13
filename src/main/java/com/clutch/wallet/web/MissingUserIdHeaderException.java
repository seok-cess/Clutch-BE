package com.clutch.wallet.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class MissingUserIdHeaderException extends RuntimeException{
    public MissingUserIdHeaderException(){
        super("X-User-Id 헤더가 필요합니다.");
    }
}
