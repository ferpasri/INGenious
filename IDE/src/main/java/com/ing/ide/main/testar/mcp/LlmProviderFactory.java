package com.ing.ide.main.testar.mcp;

/**
 * Factory for instantiating {@link LlmProvider} implementations.
 *
 * <p>
 * Reads the {@code llmProviderName} field from {@link McpAgentSettings} and
 * returns the matching provider. Three providers are available:
 * </p>
 *
 * <table border="1" cellpadding="4">
 *   <caption>Supported provider keys</caption>
 *   <tr><th>Key</th><th>Provider</th><th>Typical models</th></tr>
 *   <tr>
 *     <td>{@code "OpenAI"} (default)</td>
 *     <td>{@link OpenAiProvider}</td>
 *     <td>gpt-4o, gpt-4.1, gpt-4o-mini, o3-mini, o4-mini</td>
 *   </tr>
 *   <tr>
 *     <td>{@code "Gemini"}</td>
 *     <td>{@link GeminiProvider}</td>
 *     <td>gemini-2.5-flash, gemini-2.5-pro</td>
 *   </tr>
 *   <tr>
 *     <td>{@code "Ollama"}</td>
 *     <td>{@link OllamaProvider}</td>
 *     <td>llama3.1, llama3.2, qwen2.5:7b, qwen3:8b, ministral:8b</td>
 *   </tr>
 * </table>
 *
 * <p>
 * The provider key is case-sensitive. An unrecognised key falls back to
 * {@code OpenAI} (which will fail fast if the API key env-var is not set,
 * rather than silently using the wrong model).
 * </p>
 *
 * @author TFG-MCP-TESTAR Team
 * @version 2.0
 * @since 2025
 * @see LlmProvider
 * @see McpAgentSettings
 */
public class LlmProviderFactory {

    /**
     * Returns a fully initialised {@link LlmProvider} for the given settings.
     *
     * @param settings agent settings containing the provider name, model tag,
     *                 API key env-var name, and optional custom URL
     * @return the matching {@link LlmProvider} implementation
     * @throws IllegalStateException if the required API key env-var is not set
     *                               (only applies to cloud providers)
     */
    public static LlmProvider getProvider(McpAgentSettings settings) {
        String providerName = settings.llmProviderName != null ? settings.llmProviderName : "OpenAI";
        String apiKey       = System.getenv(settings.apiKeyEnvVarName);

        switch (providerName) {

            // ── Google Gemini ────────────────────────────────────────────────
            case "Gemini":
                return new GeminiProvider(
                        settings.apiUrl,
                        apiKey,
                        settings.openaiModel);

            // ── Local Ollama (all open-source models) ────────────────────────
            // Supported: llama3.1, llama3.2, qwen2.5:7b, qwen3:8b, ministral:8b, …
            case "Ollama":
                return new OllamaProvider(
                        resolveOllamaUrl(settings.customApiUrl),
                        settings.openaiModel);

            // ── OpenAI / Azure OpenAI (default) ─────────────────────────────
            case "OpenAI":
            default:
                if (apiKey == null || apiKey.isEmpty()) {
                    throw new IllegalStateException(
                            "Environment variable '" + settings.apiKeyEnvVarName
                            + "' is not set or is empty. "
                            + "Please export it before running INGenious.");
                }
                return new OpenAiProvider(
                        settings.apiUrl,
                        apiKey,
                        settings.openaiModel,
                        settings.vision != null && settings.vision,
                        settings.reasoningLevel);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Returns the Ollama API URL, defaulting to the local /api/chat endpoint
     * if the custom URL is not configured.
     */
    private static String resolveOllamaUrl(String customUrl) {
        return (customUrl != null && !customUrl.isBlank())
                ? customUrl
                : "http://localhost:11434/api/chat";
    }
}
