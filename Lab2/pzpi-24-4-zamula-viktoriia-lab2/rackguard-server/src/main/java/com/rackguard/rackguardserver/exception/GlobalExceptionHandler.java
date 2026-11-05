package com.rackguard.rackguardserver.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.time.OffsetDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {
    public record ApiError(String code, String message, OffsetDateTime timestamp) {}

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final MessageSource messages;

    public GlobalExceptionHandler(MessageSource messages) { this.messages = messages; }

    private ResponseEntity<ApiError> build(HttpStatusCode status, String code, Object... args) {
        String msg = messages.getMessage(code, args, code, LocaleContextHolder.getLocale());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError(code, msg, OffsetDateTime.now()));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> api(ApiException e) { return build(e.status(), e.code(), e.args()); }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> db(DataAccessException e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException first) {
                for (SQLException s = first; s != null; s = s.getNextException()) {
                    switch (s.getErrorCode()) {
                        case 50000 -> { return build(HttpStatus.CONFLICT, "error.dbRule", s.getMessage()); }
                        case 547 -> { return build(HttpStatus.CONFLICT, "error.dbConstraint"); }
                        case 2601, 2627 -> { return build(HttpStatus.CONFLICT, "error.dbDuplicate"); }
                        default -> { }
                    }
                }
            }
        }
        log.error("Database error: {}", e.getClass().getSimpleName());
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "error.internal");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> any(Exception e) {
        if (e instanceof MethodArgumentNotValidException) return build(HttpStatus.BAD_REQUEST, "error.validation");
        if (e instanceof HttpMessageNotReadableException) return build(HttpStatus.BAD_REQUEST, "error.request");
        if (e instanceof ErrorResponse er) return build(er.getStatusCode(), "error.request");
        log.error("Unhandled error: {}", e.getClass().getName());
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "error.internal");
    }
}
