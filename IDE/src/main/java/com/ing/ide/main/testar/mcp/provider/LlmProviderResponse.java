package com.ing.ide.main.testar.mcp.provider;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Provider-neutral response wrapper returned by {@link LlmProvider}.
 *
 * <p>
 * This object separates three concerns that the MCP agent loop cares about:
 * assistant output text, normalized tool calls, and provider-native assistant
 * items that should be preserved in the running conversation.
 * </p>
 */
public class LlmProviderResponse {

    private final String outputText;
    private final List<LlmToolCall> toolCalls;
    private final List<Map<String, Object>> assistantItems;

    public LlmProviderResponse(String outputText,
                               List<LlmToolCall> toolCalls,
                               List<Map<String, Object>> assistantItems) {
        this.outputText = outputText != null ? outputText : "";
        this.toolCalls = toolCalls != null ? toolCalls : Collections.emptyList();
        this.assistantItems = assistantItems != null ? assistantItems : Collections.emptyList();
    }

    public String getOutputText() {
        return outputText;
    }

    public List<LlmToolCall> getToolCalls() {
        return toolCalls;
    }

    public List<Map<String, Object>> getAssistantItems() {
        return assistantItems;
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

}
