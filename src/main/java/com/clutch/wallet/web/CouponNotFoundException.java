package com.clutch.wallet.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class CouponNotFoundException extends RuntimeException{
    public CouponNotFoundException(){
        super("쿠폰을 찾을 수 없습니다.");
    }
}
