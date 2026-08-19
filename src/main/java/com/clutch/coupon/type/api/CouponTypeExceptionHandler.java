package com.clutch.coupon.type.api;

import com.clutch.coupon.type.api.dto.CouponTypeErrorResponse;
import com.clutch.coupon.type.exception.CouponTypeErrorCode;
import com.clutch.coupon.type.exception.CouponTypeException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 관리자 쿠폰 종류 API 예외를 공통 오류 응답으로 변환한다.
 */
@RestControllerAdvice(assignableTypes = CouponTypeAdminController.class)
public class CouponTypeExceptionHandler {

    /**
     * 쿠폰 종류 관리 중 발생한 비즈니스 예외를 오류 응답으로 변환한다.
     *
     * @param exception 발생한 쿠폰 종류 비즈니스 예외
     * @return 오류 코드에 대응하는 HTTP 상태와 메시지
     */
    @ExceptionHandler(CouponTypeException.class)
    public ResponseEntity<CouponTypeErrorResponse> handleCouponTypeException(
            CouponTypeException exception
    ) {
        CouponTypeErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(new CouponTypeErrorResponse(
                        errorCode.name(),
                        exception.getMessage()
                ));
    }

    /**
     * 요청 DTO의 Bean Validation 실패를 HTTP 400 응답으로 변환한다.
     *
     * @param exception 요청 필드 검증 예외
     * @return 첫 번째 필드 오류 메시지를 담은 HTTP 400 응답
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CouponTypeErrorResponse> handleValidationException(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("잘못된 요청입니다.");
        return invalidResponse(message);
    }

    /**
     * JSON 형식 또는 Enum 변환 실패를 HTTP 400 응답으로 변환한다.
     *
     * @return 잘못된 요청 형식 메시지를 담은 HTTP 400 응답
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CouponTypeErrorResponse> handleUnreadableRequest() {
        return invalidResponse("요청 형식 또는 Enum 값이 올바르지 않습니다.");
    }

    private ResponseEntity<CouponTypeErrorResponse> invalidResponse(
            String message
    ) {
        return ResponseEntity.badRequest().body(new CouponTypeErrorResponse(
                CouponTypeErrorCode.INVALID_COUPON_TYPE_CONFIGURATION.name(),
                message
        ));
    }
}
