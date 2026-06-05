package com.ing.ide.main.testar.mcp.provider;

import com.ing.ide.main.testar.mcp.McpAgentSettings;

/**
 * Factory that resolves the MCP agent provider from persisted settings.
 *
 * <p>
 * This class is the boundary between the Swing configuration panel and the
 * provider strategy layer. It centralizes supported provider keys, runtime
 * defaults, and provider-specific construction rules so they do not leak into
 * {@code MCPAgentPanel} or {@code LlmMcpAgent}.
 * </p>
 */
public final class LlmProviderFactory {

    public static final String PROVIDER_OPENAI = "OpenAI";
    public static final String PROVIDER_GEMINI = "Gemini";
    public static final String PROVIDER_OLLAMA = "Ollama";

    private LlmProviderFactory() {
    }

    /**
     * Creates the provider implementation selected in the Studio MCP settings.
     */
    public static LlmProvider create(McpAgentSettings settings) {
        String providerName = normalizeProviderName(settings.llmProviderName);
        String model = defaultIfBlank(settings.openaiModel, defaultModelFor(providerName));
        String apiUrl = defaultIfBlank(settings.apiUrl, defaultApiUrlFor(providerName));
        String apiKeyEnvVarName = defaultIfBlank(settings.apiKeyEnvVarName, defaultApiKeyEnvVarFor(providerName));
        String apiKey = System.getenv(apiKeyEnvVarName);

        switch (providerName) {
            case PROVIDER_GEMINI:
                return new GeminiProvider(apiUrl, apiKey, model);
            case PROVIDER_OLLAMA:
                return new OllamaProvider(apiUrl, model);
            case PROVIDER_OPENAI:
            default:
                if (apiKey == null || apiKey.isBlank()) {
                    throw new IllegalStateException(
                            "Environment variable '" + apiKeyEnvVarName + "' is not set or is empty."
                    );
                }
                return new OpenAiProvider(
                        apiUrl,
                        apiKey,
                        model,
                        settings.vision != null && settings.vision,
                        settings.reasoningLevel
                );
        }
    }

    public static String[] supportedProviders() {
        return new String[] { PROVIDER_OPENAI, PROVIDER_GEMINI, PROVIDER_OLLAMA };
    }

    /**
     * Normalizes the provider name to a supported key, defaulting to OpenAI.
     */
    public static String normalizeProviderName(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return PROVIDER_OPENAI;
        }
        return providerName;
    }

    /**
     * Returns the preferred default API URL for the selected provider.
     */
    public static String defaultApiUrlFor(String providerName) {
        String normalizedProviderName = normalizeProviderName(providerName);
        switch (normalizedProviderName) {
            case PROVIDER_GEMINI:
                return "";
            case PROVIDER_OLLAMA:
                return "http://localhost:11434/api/chat";
            case PROVIDER_OPENAI:
            default:
                return "https://api.openai.com/v1/responses";
        }
    }

    /**
     * Returns the preferred API key environment variable name for the provider.
     */
    public static String defaultApiKeyEnvVarFor(String providerName) {
        String normalizedProviderName = normalizeProviderName(providerName);
        switch (normalizedProviderName) {
            case PROVIDER_GEMINI:
                return "GEMINI_API_KEY";
            case PROVIDER_OLLAMA:
                return "";
            case PROVIDER_OPENAI:
            default:
                return "OPENAI_API_KEY";
        }
    }

    /**
     * Returns the preferred default model for the provider shown in the UI.
     */
    public static String defaultModelFor(String providerName) {
        String normalizedProviderName = normalizeProviderName(providerName);
        switch (normalizedProviderName) {
            case PROVIDER_GEMINI:
                return "gemini-2.5-flash";
            case PROVIDER_OLLAMA:
                return "qwen3:8b";
            case PROVIDER_OPENAI:
            default:
                return "gpt-5.4-mini";
        }
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value != null && !value.isBlank() ? value : defaultValue;
    }

}
