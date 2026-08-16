package com.clutch.wallet.web.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class CouponAlreadyUsedException extends RuntimeException{
    public CouponAlreadyUsedException(){
        super("이미 사용된 쿠폰입니다.");
    }
}
