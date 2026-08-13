package com.clutch.coupon.claim.api;

import com.clutch.coupon.claim.api.dto.CouponClaimErrorResponse;
import com.clutch.coupon.claim.exception.CouponClaimErrorCode;
import com.clutch.coupon.claim.exception.CouponClaimException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 쿠폰 발급 요청 예외 처리기
 */
@RestControllerAdvice(
        assignableTypes = CouponClaimController.class
)
public class CouponClaimExceptionHandler {

    /**
     * 쿠폰 발급 요청 예외 처리
     *
     * @param exception 쿠폰 발급 요청 예외
     * @return 쿠폰 발급 요청 오류 응답
     */
    @ExceptionHandler(CouponClaimException.class)
    public ResponseEntity<CouponClaimErrorResponse>
    handleCouponClaimException(
            CouponClaimException exception
    ) {
        CouponClaimErrorCode errorCode =
                exception.getErrorCode();

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(CouponClaimErrorResponse.from(errorCode));
    }

    /**
     * 요청 본문 검증 예외 처리
     *
     * @param exception 요청 본문 검증 예외
     * @return 쿠폰 발급 요청 오류 응답
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CouponClaimErrorResponse>
    handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        String message = exception
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("잘못된 요청입니다.");

        return ResponseEntity
                .badRequest()
                .body(
                        CouponClaimErrorResponse.invalidRequest(
                                message
                        )
                );
    }

    /**
     * 필수 요청 헤더 누락 예외 처리
     *
     * @param exception 필수 요청 헤더 누락 예외
     * @return 쿠폰 발급 요청 오류 응답
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<CouponClaimErrorResponse>
    handleMissingRequestHeaderException(
            MissingRequestHeaderException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body(
                        CouponClaimErrorResponse.invalidRequest(
                                "X-User-Id 헤더는 필수입니다."
                        )
                );
    }
}