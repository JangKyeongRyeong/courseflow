package com.courseflow.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    private ApiException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }
    public String code() { return code; }
    public HttpStatus status() { return status; }
    public static ApiException notFound(String code, String message) { return new ApiException(code, message, HttpStatus.NOT_FOUND); }
    public static ApiException conflict(String code, String message) { return new ApiException(code, message, HttpStatus.CONFLICT); }
}

