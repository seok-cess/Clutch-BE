package com.clutch.wallet.web.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 취소할 수 없는 상태의 쿠폰을 취소하려 할 때 발생하는 예외. */
@ResponseStatus(HttpStatus.CONFLICT)
public class CouponCancelFailedException extends RuntimeException{
    public CouponCancelFailedException(){
        super("쿠폰을 취소할 수 없는 상태입니다.");
    }
}
