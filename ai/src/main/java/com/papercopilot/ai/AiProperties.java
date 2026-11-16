package com.papercopilot.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * startingCredits: free AI calls granted to a new user.
 * mcpUserId: the account the MCP tools act on until per-user MCP auth exists (development only).
 */
@ConfigurationProperties("copilot.ai")
public record AiProperties(int startingCredits, String mcpUserId) {}
