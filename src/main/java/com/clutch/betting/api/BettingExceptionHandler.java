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
public class BettingExceptionHandler {

    @ExceptionHandler(BettingException.class)
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
    public ResponseEntity<BettingErrorResponse> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest().body(new BettingErrorResponse(
                "INVALID_REQUEST",
                validationMessage(exception)
        ));
    }

    private HttpStatus statusOf(BettingErrorCode code) {
        return switch (code) {
            case EVENT_NOT_FOUND, BET_NOT_FOUND, USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE_BET -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

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
