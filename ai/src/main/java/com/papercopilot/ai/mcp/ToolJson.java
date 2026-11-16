package com.papercopilot.ai.mcp;

import java.lang.reflect.Type;

import org.springframework.ai.tool.execution.ToolCallResultConverter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/** Tool results as JSON with ISO-8601 timestamps (the default writes epoch numbers). */
public class ToolJson implements ToolCallResultConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Override
    public String convert(Object result, Type returnType) {
        try {
            return MAPPER.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise tool result", e);
        }
    }
}
