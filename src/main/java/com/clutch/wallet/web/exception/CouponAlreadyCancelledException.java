package com.clutch.wallet.web.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 이미 취소된 쿠폰을 다시 처리하려 할 때 발생하는 예외. */
@ResponseStatus(HttpStatus.CONFLICT)
public class CouponAlreadyCancelledException extends RuntimeException{
    public CouponAlreadyCancelledException(){
        super("취소된 쿠폰입니다.");
    }
}
