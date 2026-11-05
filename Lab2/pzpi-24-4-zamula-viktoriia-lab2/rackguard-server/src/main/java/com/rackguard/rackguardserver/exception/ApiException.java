package com.rackguard.rackguardserver.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final transient Object[] args;

    public ApiException(HttpStatus status, String code, Object... args) {
        super(code);
        this.status = status;
        this.code = code;
        this.args = args;
    }

    public static ApiException bad(String code, Object... a) { return new ApiException(HttpStatus.BAD_REQUEST, code, a); }
    public static ApiException notFound(String code) { return new ApiException(HttpStatus.NOT_FOUND, code); }
    public static ApiException conflict(String code, Object... a) { return new ApiException(HttpStatus.CONFLICT, code, a); }
    public static ApiException forbidden(String code) { return new ApiException(HttpStatus.FORBIDDEN, code); }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public Object[] args() { return args; }
}
