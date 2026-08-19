package com.clutch.coupon.test.event.api;

import com.clutch.coupon.event.api.dto.CouponEventErrorResponse;
import com.clutch.coupon.test.event.exception.CouponEventErrorCode;
import com.clutch.coupon.test.event.exception.CouponEventException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 수동 쿠폰 발급 테스트 예외를 API 오류 응답으로 변환한다. */
@RestControllerAdvice(assignableTypes = CouponEventAdminController.class)
public class CouponTestEventExceptionHandler {

    @ExceptionHandler(CouponEventException.class)
    public ResponseEntity<CouponEventErrorResponse> handle(
            CouponEventException exception
    ) {
        CouponEventErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(new CouponEventErrorResponse(
                        errorCode.name(),
                        exception.getMessage()
                ));
    }
}
