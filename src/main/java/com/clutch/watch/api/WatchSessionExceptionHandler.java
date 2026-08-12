package com.clutch.watch.api;

import com.clutch.watch.exception.WatchSessionError;
import com.clutch.watch.exception.WatchSessionException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 시청 세션 Controller의 예외를 일관된 JSON 오류 응답으로 변환한다.
 */
@RestControllerAdvice(assignableTypes = WatchSessionController.class)
public class WatchSessionExceptionHandler {

    /**
     * 시청 세션 비즈니스 예외를 정의된 HTTP 상태와 오류 응답으로 변환한다.
     *
     * @param exception 발생한 시청 세션 예외
     * @return 오류 코드와 한국어 메시지를 담은 응답
     */
    @ExceptionHandler(WatchSessionException.class)
    public ResponseEntity<WatchSessionErrorResponse> handleWatchSessionException(
            WatchSessionException exception
    ) {
        WatchSessionError error = exception.getError();
        return ResponseEntity.status(error.status())
                .body(new WatchSessionErrorResponse(error.name(), error.message()));
    }

    /**
     * JSON 요청 본문의 Bean Validation 실패를 400 오류로 변환한다.
     *
     * @param exception 요청 본문 검증 실패 예외
     * @return INVALID_REQUEST 오류 응답
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<WatchSessionErrorResponse> handleRequestValidation(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("요청 값이 올바르지 않습니다.");
        return invalidRequest(message);
    }

    /**
     * 경로 변수의 Bean Validation 실패를 400 오류로 변환한다.
     *
     * @param exception 경로 변수 검증 실패 예외
     * @return INVALID_REQUEST 오류 응답
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<WatchSessionErrorResponse> handlePathValidation(
            ConstraintViolationException exception
    ) {
        String message = exception.getConstraintViolations().stream()
                .findFirst()
                .map(violation -> violation.getMessage())
                .orElse("요청 값이 올바르지 않습니다.");
        return invalidRequest(message);
    }

    /**
     * 검증 오류 메시지를 공통 INVALID_REQUEST 응답으로 생성한다.
     *
     * @param message 구체적인 입력값 검증 실패 메시지
     * @return HTTP 400 오류 응답
     */
    private ResponseEntity<WatchSessionErrorResponse> invalidRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new WatchSessionErrorResponse("INVALID_REQUEST", message));
    }
}
