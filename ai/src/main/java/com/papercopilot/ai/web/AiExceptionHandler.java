package com.papercopilot.ai.web;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.papercopilot.ai.AiUnavailableException;
import com.papercopilot.ai.credits.AiCreditsExhaustedException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AiExceptionHandler.class);

    @ExceptionHandler(AiCreditsExhaustedException.class)
    ResponseEntity<Map<String, String>> creditsExhausted(AiCreditsExhaustedException e) {
        return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                .body(Map.of("code", "AI_CREDITS_EXHAUSTED", "message", e.getMessage()));
    }

    @ExceptionHandler(AiUnavailableException.class)
    ResponseEntity<Map<String, String>> unavailable(AiUnavailableException e) {
        log.warn("AI call failed", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("code", "AI_UNAVAILABLE", "message", e.getMessage()));
    }
}
