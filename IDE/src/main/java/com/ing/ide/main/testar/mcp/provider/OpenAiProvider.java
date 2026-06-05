package com.ing.ide.main.testar.mcp.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OpenAI Responses API implementation of the {@link LlmProvider} contract.
 *
 * <p>
 * This provider keeps the current MCP behavior: native tool calling,
 * image attachment support, reasoning-effort configuration, and usage tracking
 * through the OpenAI response payload.
 * </p>
 */
public class OpenAiProvider implements LlmProvider {

    private static final Logger LOGGER = Logger.getLogger(OpenAiProvider.class.getName());

    private final OkHttpClient client;
    private final ObjectMapper mapper;
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final boolean visionEnabled;
    private final String reasoningLevel;

    private List<Map<String, Object>> registeredTools = Collections.emptyList();
    private volatile int lastTokenUsage = 0;

    public OpenAiProvider(String apiUrl,
                          String apiKey,
                          String model,
                          boolean visionEnabled,
                          String reasoningLevel) {
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.visionEnabled = visionEnabled;
        this.reasoningLevel = reasoningLevel;
        this.mapper = new ObjectMapper();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.MINUTES)
                .retryOnConnectionFailure(true)
                .build();
    }

    @Override
    public void registerTools(List<Map<String, Object>> tools) {
        this.registeredTools = tools != null ? tools : Collections.emptyList();
    }

    /**
     * Executes one provider turn using the OpenAI Responses API.
     */
    @Override
    public LlmProviderResponse executePrompt(String systemPrompt,
                                             List<Map<String, Object>> input,
                                             boolean visionRequested) throws LlmProviderException {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("tools", registeredTools);
        body.put("tool_choice", "auto");
        if (shouldIncludeReasoningEffort()) {
            body.put("reasoning", Map.of("effort", reasoningLevel));
        }
        body.put("instructions", systemPrompt);
        body.put("input", input);

        String requestJson;
        try {
            requestJson = mapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new LlmProviderException("Failed to serialize OpenAI request body", exception);
        }

        Request request = new Request.Builder()
                .url(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Copilot-Vision-Request", String.valueOf(visionRequested))
                .post(RequestBody.create(MediaType.parse("application/json"), requestJson))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                if (response.code() == 429) {
                    long waitTime = parseRetryAfter(response.header("Retry-After"));
                    throw new LlmProviderException("OpenAI rate limited (429)", response.code(), true, waitTime);
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                LOGGER.log(Level.SEVERE, "OpenAI API error. Status: {0}, Body: {1}",
                        new Object[] { response.code(), responseBody });
                throw new LlmProviderException("OpenAI API call failed with status: " + response.code(),
                        response.code(), false, 0L);
            }

            String responseJson = Objects.requireNonNull(response.body()).string();
            Map<?, ?> parsed = mapper.readValue(responseJson, Map.class);
            logTokenUsage((Map<?, ?>) parsed.get("usage"));

            List<Map<String, Object>> outputItems = castListOfMaps(parsed.get("output"));
            List<LlmToolCall> toolCalls = findFunctionCalls(outputItems);
            String outputText = asText(parsed.get("output_text"));

            return new LlmProviderResponse(outputText, toolCalls, outputItems);
        } catch (LlmProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new LlmProviderException("OpenAI API communication failed", exception);
        }
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public boolean supportsVision() {
        if (!visionEnabled) {
            return false;
        }

        String normalizedModel = model == null ? "" : model.toLowerCase();
        return normalizedModel.contains("gpt-4o")
                || normalizedModel.contains("gpt-4.1")
                || normalizedModel.contains("gpt-5");
    }

    @Override
    public boolean isReasoningModel() {
        String normalizedModel = model == null ? "" : model.toLowerCase();
        return normalizedModel.startsWith("gpt-5");
    }

    @Override
    public int getLastTokenUsage() {
        return lastTokenUsage;
    }

    private boolean shouldIncludeReasoningEffort() {
        return isReasoningModel()
                && reasoningLevel != null
                && !reasoningLevel.isBlank()
                && !"none".equalsIgnoreCase(reasoningLevel);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castListOfMaps(Object value) {
        if (!(value instanceof List<?>)) {
            return Collections.emptyList();
        }
        return (List<Map<String, Object>>) value;
    }

    private List<LlmToolCall> findFunctionCalls(List<Map<String, Object>> outputItems) {
        List<LlmToolCall> toolCalls = new ArrayList<>();
        for (Map<String, Object> item : outputItems) {
            if (!"function_call".equals(asText(item.get("type")))) {
                continue;
            }

            toolCalls.add(new LlmToolCall(
                    asText(item.get("call_id")),
                    asText(item.get("name")),
                    asText(item.get("arguments"))
            ));
        }
        return toolCalls;
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private long parseRetryAfter(String retryAfterHeader) {
        long defaultWait = 10000L;
        if (retryAfterHeader == null || retryAfterHeader.isBlank()) {
            return defaultWait;
        }

        try {
            return Long.parseLong(retryAfterHeader) * 1333L;
        } catch (NumberFormatException exception) {
            LOGGER.log(Level.WARNING, "Invalid Retry-After header value: {0}", retryAfterHeader);
            return defaultWait;
        }
    }

    private void logTokenUsage(Map<?, ?> usage) {
        if (usage == null || usage.isEmpty()) {
            lastTokenUsage = 0;
            return;
        }

        Number totalTokens = asNumber(usage.get("total_tokens"));
        lastTokenUsage = totalTokens != null ? totalTokens.intValue() : 0;
    }

    private Number asNumber(Object value) {
        return value instanceof Number ? (Number) value : null;
    }

}
