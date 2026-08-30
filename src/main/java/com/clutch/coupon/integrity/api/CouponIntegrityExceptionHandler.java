package com.clutch.coupon.integrity.api;

import com.clutch.coupon.integrity.api.dto.CouponIntegrityErrorResponse;
import com.clutch.coupon.integrity.service.CouponIntegrityErrorCode;
import com.clutch.coupon.integrity.service.CouponIntegrityException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CouponIntegrityAdminController.class)
public class CouponIntegrityExceptionHandler {
    @ExceptionHandler(CouponIntegrityException.class)
    public ResponseEntity<CouponIntegrityErrorResponse> handle(CouponIntegrityException exception) {
        CouponIntegrityErrorCode code = exception.getErrorCode();
        return ResponseEntity.status(code.httpStatus()).body(
                new CouponIntegrityErrorResponse(code.name(), exception.getMessage())
        );
    }
}
