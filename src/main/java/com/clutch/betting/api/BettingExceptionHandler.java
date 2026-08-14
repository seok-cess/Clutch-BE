package com.clutch.betting.api;

import com.clutch.betting.api.dto.BettingErrorResponse;
import com.clutch.betting.exception.BettingErrorCode;
import com.clutch.betting.exception.BettingException;
import com.clutch.wallet.web.MissingUserIdHeaderException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = BettingController.class)
/** 배팅 API에서 발생한 도메인·요청 검증 예외를 공통 응답으로 변환한다. */
public class BettingExceptionHandler {

    @ExceptionHandler(BettingException.class)
    /** 배팅 오류 코드를 HTTP 상태와 오류 응답으로 매핑한다. */
    public ResponseEntity<BettingErrorResponse> handleBettingException(
            BettingException exception
    ) {
        BettingErrorCode code = exception.getErrorCode();
        return ResponseEntity.status(statusOf(code))
                .body(new BettingErrorResponse(code.name(), code.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class,
            MissingUserIdHeaderException.class
    })
    /** 요청 본문·경로·사용자 헤더 검증 실패를 400 응답으로 통합한다. */
    public ResponseEntity<BettingErrorResponse> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new BettingErrorResponse(
                "INVALID_REQUEST",
                validationMessage(exception)
        ));
    }

    /** 오류 코드의 의미에 맞는 HTTP 상태를 선택한다. */
    private HttpStatus statusOf(BettingErrorCode code) {
        return switch (code) {
            case EVENT_NOT_FOUND, BET_NOT_FOUND, USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE_BET -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    /** 검증 예외에서 클라이언트에 전달할 첫 번째 유효 메시지를 추출한다. */
    private String validationMessage(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException validationException) {
            return validationException.getBindingResult().getFieldErrors().stream()
                    .findFirst()
                    .map(error -> error.getDefaultMessage())
                    .orElse("요청 값이 올바르지 않습니다.");
        }
        if (exception instanceof ConstraintViolationException violationException) {
            return violationException.getConstraintViolations().stream()
                    .findFirst()
                    .map(violation -> violation.getMessage())
                    .orElse("요청 값이 올바르지 않습니다.");
        }
        return exception.getMessage() == null
                ? "요청 값이 올바르지 않습니다."
                : exception.getMessage();
    }
}
