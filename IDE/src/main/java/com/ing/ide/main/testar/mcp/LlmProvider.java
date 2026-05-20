package com.ing.ide.main.testar.mcp;

import java.util.List;
import java.util.Map;

/**
 * Interface defining the contract for Large Language Model (LLM) providers.
 * 
 * <p>
 * This interface is part of a <strong>Multi-LLM Architecture</strong> designed
 * to support
 * multiple AI providers (OpenAI, Google Gemini, Meta Llama, etc.) for the
 * following reasons:
 * </p>
 * 
 * <ul>
 * <li><strong>GDPR Compliance</strong>: Allows switching to privacy-focused or
 * on-premise models</li>
 * <li><strong>Cost Optimization</strong>: Enables using different providers
 * based on pricing</li>
 * <li><strong>Flexibility</strong>: Easy integration of new LLM providers
 * without modifying core logic</li>
 * <li><strong>Resilience</strong>: Fallback to alternative providers if one
 * becomes unavailable</li>
 * </ul>
 * 
 * <p>
 * Implementations of this interface should handle all HTTP communication,
 * authentication,
 * and JSON serialization specific to their respective LLM provider.
 * </p>
 * 
 * <h2>Design Pattern: Strategy</h2>
 * <p>
 * This interface follows the Strategy Pattern, allowing the {@link LlmMcpAgent}
 * to
 * switch between different LLM implementations at runtime without changing its
 * behavior.
 * </p>
 * 
 * @author TFG-MCP-TESTAR Team
 * @version 1.0
 * @since 2024
 * @see OpenAiProvider
 * @see GeminiProvider
 * @see OllamaProvider
 * @see LlmProviderFactory
 * @see LlmMcpAgent
 */
public interface LlmProvider {

    /**
     * Executes a prompt and retrieves the response, enforcing JSON output.
     * 
     * <p>
     * This method is the core of the abstract provider interface. Irrespective of
     * the underlying LLM's API structure, it must return a valid JSON string
     * containing the requested thought and action.
     * </p>
     * 
     * @param systemPrompt The strict system prompt (Split + Verbose instructions)
     * @param userContext  The current state/DOM data from the system under test
     * @return A valid JSON string with the LLM's response
     * @throws LlmProviderException if the request fails
     */
    String executePrompt(String systemPrompt, String userContext) throws LlmProviderException;

    /**
     * Registers the available MCP tools with the provider.
     * 
     * <p>
     * Depending on the provider implementation, these tools might be passed via
     * native tool_calling APIs or injected into the system prompt as a JSON schema.
     * </p>
     * 
     * @param tools List of tools following the MCP/OpenAI function calling format
     */
    void registerTools(List<Map<String, Object>> tools);

    /**
     * Returns the name/identifier of the model being used by this provider.
     * 
     * @return the model name string
     */
    String getModelName();

    /**
     * Checks if the current model supports vision/image input.
     * 
     * @return {@code true} if the model can process images, {@code false} otherwise
     */
    boolean supportsVision();

    /**
     * Checks if the current model is a reasoning model that supports
     * reasoning effort configuration.
     *
     * @return {@code true} if the model supports reasoning effort parameter
     */
    boolean isReasoningModel();

    /**
     * Returns the total number of tokens consumed in the last call to
     * {@link #executePrompt(String, String)} (prompt + completion combined).
     *
     * <p>Implementations that do not expose token counts should return {@code 0}.
     * The default implementation returns {@code 0} for backward compatibility.</p>
     *
     * @return token count from the last API call, or {@code 0} if unavailable
     */
    default int getLastTokenUsage() {
        return 0;
    }
}
