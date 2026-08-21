package com.clutch.wallet.web.exception;

import com.clutch.wallet.web.AdminCouponController;
import com.clutch.wallet.web.MyCouponController;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = {MyCouponController.class, AdminCouponController.class})
public class WalletExceptionHandler extends RuntimeException {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception exception){
        ResponseStatus annotation = AnnotationUtils.findAnnotation(exception.getClass(), ResponseStatus.class);

        if(annotation != null){
            return ResponseEntity.status(annotation.value())
                    .body(Map.of("message", exception.getMessage()));
        }

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("message", "요청을 처리할 수 없습니다."));
    }
}