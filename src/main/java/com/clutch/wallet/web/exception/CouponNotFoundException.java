package com.clutch.wallet.web.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** 요청한 사용자 쿠폰을 찾을 수 없을 때 발생하는 예외. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class CouponNotFoundException extends RuntimeException{
    public CouponNotFoundException(){
        super("쿠폰을 찾을 수 없습니다.");
    }
}
