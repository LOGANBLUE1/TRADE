package com.papercopilot.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@Import(TestSupport.class)
class McpToolSchemaTest {

    @Autowired ToolCallbackProvider provider;

    @Test
    void exposesExactlyTheIntendedTools_andNoneCanApprove() {
        List<String> names = Arrays.stream(provider.getToolCallbacks())
                .map(t -> t.getToolDefinition().name()).toList();
        assertThat(names).containsExactlyInAnyOrder("get_quote", "screen_stocks", "get_portfolio",
                "propose_trade", "get_order", "execute_trade");
    }

    /** A condition sets value OR otherField, so the published schema must not require both. */
    @Test
    void screenFilterSchema_requiresOnlyFieldAndOp() throws Exception {
        ToolCallback screen = Arrays.stream(provider.getToolCallbacks())
                .filter(t -> t.getToolDefinition().name().equals("screen_stocks")).findFirst().orElseThrow();
        JsonNode filter = new ObjectMapper().readTree(screen.getToolDefinition().inputSchema())
                .path("properties").path("filter");
        assertThat(texts(filter.path("required"))).containsExactly("conditions");
        assertThat(texts(filter.path("properties").path("conditions").path("items").path("required")))
                .containsExactlyInAnyOrder("field", "op");
    }

    private static List<String> texts(JsonNode array) {
        return java.util.stream.StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }
}
