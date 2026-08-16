package com.clutch.user.api;

import com.clutch.user.dto.response.UserErrorResponse;
import com.clutch.user.exception.UserNotFoundException;
import com.clutch.wallet.web.exception.MissingUserIdHeaderException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 사용자 조회 API 예외를 일관된 JSON 오류 응답으로 변환한다. */
@RestControllerAdvice(assignableTypes = UserController.class)
public class UserExceptionHandler {

    /** 존재하지 않는 사용자를 404 응답으로 변환한다. */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<UserErrorResponse> handleUserNotFound(UserNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new UserErrorResponse(
                "USER_NOT_FOUND",
                exception.getMessage()
        ));
    }

    /** 누락되거나 잘못된 사용자 헤더를 400 응답으로 변환한다. */
    @ExceptionHandler(MissingUserIdHeaderException.class)
    public ResponseEntity<UserErrorResponse> handleInvalidUserHeader(
            MissingUserIdHeaderException exception
    ) {
        return ResponseEntity.badRequest().body(new UserErrorResponse(
                "INVALID_REQUEST",
                exception.getMessage()
        ));
    }
}
