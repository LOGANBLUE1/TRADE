package com.papercopilot.ai.web;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.papercopilot.ai.credits.AiCreditService;
import com.papercopilot.ai.credits.AiCreditsExhaustedException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** The credit gate. Registered for /api/ai/** only, so it cannot block a core endpoint. */
@Component
public class AiCreditInterceptor implements HandlerInterceptor {

    private final AiCreditService credits;

    public AiCreditInterceptor(AiCreditService credits) {
        this.credits = credits;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = request.getHeader("X-User-Id");
        if (credits.balance(userId == null || userId.isBlank() ? "demo" : userId) <= 0) {
            throw new AiCreditsExhaustedException();
        }
        return true;
    }
}
