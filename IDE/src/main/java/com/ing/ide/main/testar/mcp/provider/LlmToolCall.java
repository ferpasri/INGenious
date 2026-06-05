package com.ing.ide.main.testar.mcp.provider;

/**
 * Provider-neutral representation of one model-selected tool invocation.
 *
 * <p>
 * Providers may receive native tool-calling responses or plain JSON text.
 * This DTO normalizes both styles into the single shape expected by
 * {@code LlmMcpAgent}.
 * </p>
 */
public class LlmToolCall {

    private final String callId;
    private final String toolName;
    private final String argumentsJson;

    public LlmToolCall(String callId, String toolName, String argumentsJson) {
        this.callId = callId;
        this.toolName = toolName;
        this.argumentsJson = argumentsJson;
    }

    public String getCallId() {
        return callId;
    }

    public String getToolName() {
        return toolName;
    }

    public String getArgumentsJson() {
        return argumentsJson;
    }

}
