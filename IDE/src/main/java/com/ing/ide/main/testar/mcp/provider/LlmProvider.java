package com.ing.ide.main.testar.mcp.provider;

import java.util.List;
import java.util.Map;

/**
 * Interface defining the contract for Large Language Model (LLM) providers.
 * 
 * <p>
 * This interface is part of a <strong>Multi-LLM Architecture</strong> designed
 * to support multiple AI providers (OpenAI, Google Gemini, Meta Llama, etc.).
 * </p>
 */
public interface LlmProvider {

    /**
     * Executes a prompt and retrieves the response.
     */
    LlmProviderResponse executePrompt(String systemPrompt,
                                      List<Map<String, Object>> input,
                                      boolean visionRequested) throws LlmProviderException;

    /**
     * Registers the available MCP tools with the provider.
     */
    void registerTools(List<Map<String, Object>> tools);

    /**
     * Returns the name/identifier of the model being used by this provider.
     */
    String getModelName();

    /**
     * Checks if the current model supports vision/image input.
     */
    boolean supportsVision();

    /**
     * Checks if the current model is a reasoning model that supports 
     * reasoning effort configuration.
     */
    boolean isReasoningModel();

    /**
     * Returns the total number of tokens consumed in the last prompt call.
     */
    default int getLastTokenUsage() {
        return 0;
    }

}
