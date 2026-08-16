package com.clutch.coupon.event.api;

import com.clutch.coupon.event.api.dto.CouponEventErrorResponse;
import com.clutch.coupon.event.exception.CouponEventErrorCode;
import com.clutch.coupon.event.exception.CouponEventException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 관리자 쿠폰 이벤트 API에서 발생한 예외를 공통 오류 응답으로 변환한다.
 */
@RestControllerAdvice(assignableTypes = CouponEventAdminController.class)
public class CouponEventExceptionHandler {

    /**
     * 쿠폰 이벤트 비즈니스 예외를 정의된 HTTP 상태와 오류 코드로 변환한다.
     *
     * @param exception 처리할 쿠폰 이벤트 예외
     * @return 오류 코드와 메시지를 포함한 HTTP 응답
     */
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

    /**
     * 요청 본문 검증 실패 중 첫 번째 오류를 잘못된 요청 응답으로 변환한다.
     *
     * @param exception 요청 값 검증 예외
     * @return HTTP 400 오류 응답
     */
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
