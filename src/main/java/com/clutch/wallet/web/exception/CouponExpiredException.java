package com.clutch.wallet.web.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class CouponExpiredException extends RuntimeException{
    public CouponExpiredException(){
        super("만료된 쿠폰입니다.");
    }
}
