package com.ing.ide.main.testar.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OpenAI/Azure OpenAI implementation of the {@link LlmProvider} interface.
 * 
 * <p>
 * This class is part of the <strong>Multi-LLM Architecture</strong>
 * implementing
 * the Strategy Pattern. It handles all HTTP communication, authentication, and
 * JSON serialization specific to the OpenAI API.
 * </p>
 * 
 * <h2>Features</h2>
 * <ul>
 * <li>Uses {@link java.net.http.HttpClient} (Java 11+) for HTTP
 * communication</li>
 * <li>Supports OpenAI API and Azure OpenAI endpoints</li>
 * <li>Handles rate limiting (HTTP 429) with automatic retry suggestions</li>
 * <li>Supports vision models (GPT-4o, GPT-4.1, GPT-5)</li>
 * <li>Supports reasoning models with configurable effort levels</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * LlmProvider provider = new OpenAiProvider(
 *         "https://api.openai.com/v1/chat/completions",
 *         System.getenv("OPENAI_API_KEY"),
 *         "gpt-4o",
 *         true, // vision enabled
 *         "medium" // reasoning effort
 * );
 * 
 * String response = provider.getNextAction(messages, tools, false);
 * }</pre>
 * 
 * @author TFG-MCP-TESTAR Team
 * @version 1.0
 * @since 2024
 * @see LlmProvider
 * @see LlmMcpAgent
 */
public class OpenAiProvider implements LlmProvider {

    private static final Logger LOGGER = Logger.getLogger(OpenAiProvider.class.getName());

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final boolean visionEnabled;
    private final String reasoningLevel;

    private List<Map<String, Object>> registeredTools;

    /** Total tokens consumed in the last {@link #executePrompt} call (prompt + completion). */
    private volatile int lastTokenUsage = 0;

    /**
     * Constructs a new OpenAiProvider with the specified configuration.
     * 
     * @param apiUrl         the OpenAI API endpoint URL
     *                       (e.g., "https://api.openai.com/v1/chat/completions")
     * @param apiKey         the API key for authentication (not the env variable
     *                       name)
     * @param model          the model identifier (e.g., "gpt-4o", "gpt-4.1-mini")
     * @param visionEnabled  whether vision/image input is enabled
     * @param reasoningLevel the reasoning effort level for reasoning models
     *                       (e.g., "low", "medium", "high")
     * @throws IllegalArgumentException if apiUrl, apiKey, or model is null/empty
     */
    public OpenAiProvider(String apiUrl, String apiKey, String model,
            boolean visionEnabled, String reasoningLevel) {
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalArgumentException("API URL cannot be null or empty");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API Key cannot be null or empty");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Model cannot be null or empty");
        }

        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.visionEnabled = visionEnabled;
        this.reasoningLevel = reasoningLevel;

        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public void registerTools(List<Map<String, Object>> tools) {
        this.registeredTools = tools;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * Sends a chat completion request to the OpenAI API with the provided
     * messages and tools, enforcing JSON output format.
     * </p>
     */
    @Override
    public String executePrompt(String systemPrompt, String userContext) throws LlmProviderException {

        // Build the messages list for OpenAI, matching the 'Split + Verbose' strategy
        List<Map<String, Object>> messages = new java.util.ArrayList<>();

        messages.add(Map.of(
                "role", "system",
                "content", systemPrompt));

        // Inject tool schema directly into the prompt to ensure JSON strategy works
        // effectively
        if (registeredTools != null && !registeredTools.isEmpty()) {
            try {
                String toolsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(registeredTools);
                messages.add(Map.of(
                        "role", "system",
                        "content", "Use the following tools/functions to fill the 'parameters' field in your action:\n"
                                + toolsJson));
            } catch (JsonProcessingException e) {
                LOGGER.log(Level.WARNING, "Failed to serialize tools for system prompt", e);
            }
        }

        messages.add(Map.of(
                "role", "user",
                "content", userContext));

        Map<String, Object> requestBody = buildRequestBody(messages);
        String jsonBody;

        try {
            jsonBody = objectMapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException e) {
            throw new LlmProviderException("Failed to serialize request body", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofMinutes(20))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Copilot-Vision-Request", String.valueOf(supportsVision()))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();

            if (statusCode == 429) {
                // Rate limited
                String retryAfterHeader = response.headers()
                        .firstValue("Retry-After")
                        .orElse(null);
                long waitTime = parseRetryAfter(retryAfterHeader);

                LOGGER.log(Level.WARNING, "OpenAI rate limited (429). Retry after: {0}ms", waitTime);

                throw new LlmProviderException(
                        "OpenAI rate limited (429)",
                        429, true, waitTime);
            }

            if (statusCode < 200 || statusCode >= 300) {
                String errorBody = response.body();
                LOGGER.log(Level.SEVERE, "OpenAI API error. Status: {0}, Body: {1}",
                        new Object[] { statusCode, errorBody });

                logRequestDetails(request, jsonBody);

                throw new LlmProviderException(
                        "OpenAI API call failed with status: " + statusCode,
                        statusCode, false, 0);
            }

            // Successful response
            String responseBody = response.body();
            logTokenUsage(responseBody);

            // Extract the actual JSON string from choices.message.content
            try {
                Map<?, ?> parsed = objectMapper.readValue(responseBody, Map.class);

                @SuppressWarnings("unchecked")
                List<Map<?, ?>> choices = (List<Map<?, ?>>) parsed.get("choices");
                Map<?, ?> choice = choices.get(0);

                Map<?, ?> message = (Map<?, ?>) choice.get("message");
                String content = (String) message.get("content");

                // Clean markdown code blocks if present
                if (content != null) {
                    content = content.trim();
                    if (content.startsWith("```json")) {
                        content = content.substring(7);
                    }
                    if (content.endsWith("```")) {
                        content = content.substring(0, content.length() - 3);
                    }
                    return content.trim();
                }
            } catch (Exception e) {
                throw new LlmProviderException("Failed to parse OpenAI JSON response", e);
            }

            return responseBody;

        } catch (IOException e) {
            throw new LlmProviderException("Network error during OpenAI API call", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmProviderException("Request interrupted", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getModelName() {
        return model;
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * Vision is supported for GPT-4o, GPT-4.1, and GPT-5 models when
     * vision is enabled in the constructor.
     * </p>
     */
    @Override
    public boolean supportsVision() {
        if (!visionEnabled) {
            return false;
        }
        String m = model.toLowerCase();
        return m.contains("gpt-4o") || m.contains("gpt-4.1") || m.contains("gpt-5");
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * Currently, only GPT-5 models support reasoning effort configuration.
     * </p>
     */
    @Override
    public boolean isReasoningModel() {
        return model != null && model.toLowerCase().startsWith("gpt-5");
    }

    /**
     * Builds the request body Map for the OpenAI API.
     */
    private Map<String, Object> buildRequestBody(List<Map<String, Object>> messages) {
        Map<String, Object> body = new java.util.HashMap<>();

        // Model and tools first (static content for KV cache optimization)
        body.put("model", model);

        // Use JSON response format for OpenAI
        body.put("response_format", Map.of("type", "json_object"));

        if (isReasoningModel() && reasoningLevel != null && !reasoningLevel.isBlank()
                && !reasoningLevel.equals("none")) {
            body.put("reasoning_effort", reasoningLevel);
        }

        // Messages last (variable content)
        body.put("messages", messages);

        return body;
    }

    /**
     * Parses the Retry-After header value to milliseconds.
     */
    private long parseRetryAfter(String retryAfterHeader) {
        long defaultWait = 10000L; // 10 seconds default

        if (retryAfterHeader == null) {
            return defaultWait;
        }

        try {
            // Retry-After is in seconds, convert to ms with 33% margin
            return Long.parseLong(retryAfterHeader) * 1333L;
        } catch (NumberFormatException e) {
            LOGGER.log(Level.WARNING, "Invalid Retry-After header: {0}", retryAfterHeader);
            return defaultWait;
        }
    }

    /**
     * Logs the request details for debugging purposes.
     */
    private void logRequestDetails(HttpRequest request, String body) {
        try {
            String prettyBody = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(objectMapper.readValue(body, Map.class));
            LOGGER.log(Level.SEVERE, "Request headers: {0}", request.headers().map());
            LOGGER.log(Level.SEVERE, "Request body: {0}", prettyBody);
        } catch (JsonProcessingException e) {
            LOGGER.log(Level.SEVERE, "Could not format request body for logging");
        }
    }

    /**
     * Logs token usage from the API response and stores the total for metrics.
     */
    private void logTokenUsage(String responseBody) {
        try {
            Map<?, ?> parsed = objectMapper.readValue(responseBody, Map.class);
            Map<?, ?> usage = (Map<?, ?>) parsed.get("usage");

            if (usage == null || usage.isEmpty()) {
                LOGGER.log(Level.INFO, "Token usage: not provided by API");
                return;
            }

            Number promptTokens    = asNumber(usage.get("prompt_tokens"));
            Number completionTokens = asNumber(usage.get("completion_tokens"));
            Number totalTokens     = asNumber(usage.get("total_tokens"));

            if (totalTokens != null) {
                this.lastTokenUsage = totalTokens.intValue();
            }

            LOGGER.log(Level.INFO,
                    "Token usage: prompt={0}, completion={1}, total={2}",
                    new Object[] { promptTokens, completionTokens, totalTokens });

        } catch (JsonProcessingException e) {
            LOGGER.log(Level.WARNING, "Could not parse token usage from response");
        }
    }

    @Override
    public int getLastTokenUsage() {
        return lastTokenUsage;
    }

    /**
     * Safely converts an object to a Number.
     */
    private Number asNumber(Object value) {
        return value instanceof Number ? (Number) value : null;
    }
}
