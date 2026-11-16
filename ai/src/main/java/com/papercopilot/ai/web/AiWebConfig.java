package com.papercopilot.ai.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AiWebConfig implements WebMvcConfigurer {

    public static final String AI_PATHS = "/api/ai/**";

    private final AiCreditInterceptor creditInterceptor;

    public AiWebConfig(AiCreditInterceptor creditInterceptor) {
        this.creditInterceptor = creditInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(creditInterceptor).addPathPatterns(AI_PATHS);
    }
}
