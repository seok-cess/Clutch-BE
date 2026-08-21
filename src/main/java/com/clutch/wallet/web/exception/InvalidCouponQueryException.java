package com.clutch.wallet.web.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidCouponQueryException extends RuntimeException {
    public InvalidCouponQueryException(String message) {
        super(message);
    }
}
