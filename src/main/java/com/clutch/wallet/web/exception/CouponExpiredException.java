package com.clutch.wallet.web.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 만료된 쿠폰을 사용하려 할 때 발생하는 예외. */
@ResponseStatus(HttpStatus.CONFLICT)
public class CouponExpiredException extends RuntimeException{
    public CouponExpiredException(){
        super("만료된 쿠폰입니다.");
    }
}
