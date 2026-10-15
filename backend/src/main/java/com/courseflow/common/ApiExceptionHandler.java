package com.courseflow.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handle(ApiException exception) {
        return ResponseEntity.status(exception.status()).body(
                new ApiError(exception.code(), exception.getMessage(), Instant.now()));
    }
    public record ApiError(String code, String message, Instant timestamp) {}
}

