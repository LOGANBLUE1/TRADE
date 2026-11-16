package com.papercopilot.app.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.papercopilot.core.error.DomainException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DomainException.class)
    ResponseEntity<Map<String, String>> domain(DomainException e) {
        HttpStatus status = switch (e.getCode()) {
            case ORDER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_REQUEST, INVALID_FILTER -> HttpStatus.BAD_REQUEST;
            case QUOTE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case APPROVAL_REQUIRED, APPROVAL_EXPIRED, INVALID_STATE, IDEMPOTENCY_CONFLICT, PRICE_MOVED ->
                    HttpStatus.CONFLICT;
            case SYMBOL_NOT_ALLOWED, ORDER_TOO_LARGE, DAILY_LIMIT_REACHED, INSUFFICIENT_FUNDS,
                 INSUFFICIENT_HOLDINGS -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status).body(Map.of("code", e.getCode().name(), "message", e.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingRequestHeaderException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String, String>> badRequest(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("code", "INVALID_REQUEST",
                "message", "The request could not be read: check the body, headers and ids."));
    }
}
