package com.clutch.wallet.web.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 사용할 수 없는 상태의 쿠폰을 사용하려 할 때 발생하는 예외. */
@ResponseStatus(HttpStatus.CONFLICT)
public class CouponUseFailedException extends RuntimeException{
    public CouponUseFailedException(){
        super("쿠폰을 사용할 수 없는 상태입니다.");
    }
}
