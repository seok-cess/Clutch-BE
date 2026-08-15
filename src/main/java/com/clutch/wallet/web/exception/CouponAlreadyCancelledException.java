package com.clutch.wallet.web.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class CouponAlreadyCancelledException extends RuntimeException{
    public CouponAlreadyCancelledException(){
        super("취소된 쿠폰입니다.");
    }
}
