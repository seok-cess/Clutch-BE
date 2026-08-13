package com.clutch.coupon.event.api;

import com.clutch.coupon.event.api.dto.CouponEventErrorResponse;
import com.clutch.coupon.event.exception.CouponEventErrorCode;
import com.clutch.coupon.event.exception.CouponEventException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CouponEventAdminController.class)
public class CouponEventExceptionHandler {

    @ExceptionHandler(CouponEventException.class)
    public ResponseEntity<CouponEventErrorResponse> handleCouponEventException(
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CouponEventErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("잘못된 요청입니다.");

        return ResponseEntity.badRequest().body(
                new CouponEventErrorResponse(
                        CouponEventErrorCode.INVALID_EVENT_CONFIGURATION.name(),
                        message
                )
        );
    }
}
