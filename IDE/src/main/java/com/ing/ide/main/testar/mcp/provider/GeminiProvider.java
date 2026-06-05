package com.ing.ide.main.testar.mcp.provider;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Google Gemini implementation of the {@link LlmProvider} contract.
 *
 * <p>
 * This provider uses Gemini native function declarations through the
 * {@code generateContent} endpoint. It translates MCP tools to Gemini
 * {@code functionDeclarations}, parses native {@code functionCall} parts,
 * and sends follow-up {@code functionResponse} parts with the matching call id.
 * </p>
 */
public class GeminiProvider implements LlmProvider {

    private static final Logger LOGGER = Logger.getLogger(GeminiProvider.class.getName());

    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private List<Map<String, Object>> registeredTools = Collections.emptyList();
    private final Map<String, String> toolNameByCallId = new HashMap<>();
    private volatile int lastTokenUsage = 0;

    public GeminiProvider(String apiUrl, String apiKey, String model) {
        this.model = model != null && !model.isBlank() ? model : "gemini-2.5-flash";
        this.apiUrl = apiUrl != null && !apiUrl.isBlank()
                ? apiUrl
                : "https://generativelanguage.googleapis.com/v1beta/models/" + this.model + ":generateContent";
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Environment variable for Gemini API key is not set or is empty.");
        }

        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Executes one provider turn using the Gemini generateContent endpoint.
     */
    @Override
    public LlmProviderResponse executePrompt(String systemPrompt,
                                             List<Map<String, Object>> input,
                                             boolean visionRequested) throws LlmProviderException {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("system_instruction", Map.of(
                    "parts", List.of(Map.of("text", systemPrompt))
            ));
            requestBody.put("contents", buildContents(input));
            if (!registeredTools.isEmpty()) {
                requestBody.put("tools", buildGeminiTools());
            }

            String requestJson = objectMapper.writeValueAsString(requestBody);
            String encodedKey = URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "?key=" + encodedKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                LOGGER.log(Level.SEVERE, "Gemini API error. Status: {0}, Body: {1}",
                        new Object[] { response.statusCode(), response.body() });
                throw new LlmProviderException("Gemini API error: " + response.statusCode(),
                        response.statusCode(), false, 0L);
            }

            Map<?, ?> parsed = objectMapper.readValue(response.body(), Map.class);
            logTokenUsage(parsed);
            return toActionResponse(parsed);
        } catch (LlmProviderException exception) {
            throw exception;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new LlmProviderException("Communication error with Gemini API", exception);
        }
    }

    @Override
    public void registerTools(List<Map<String, Object>> tools) {
        this.registeredTools = tools != null ? tools : Collections.emptyList();
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public boolean supportsVision() {
        return false;
    }

    @Override
    public boolean isReasoningModel() {
        String normalizedModel = model.toLowerCase();
        return normalizedModel.contains("thinking");
    }

    @Override
    public int getLastTokenUsage() {
        return lastTokenUsage;
    }

    private List<Map<String, Object>> buildContents(List<Map<String, Object>> input) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (Map<String, Object> item : input) {
            String type = asText(item.get("type"));
            if ("function_call_output".equals(type)) {
                contents.add(createFunctionResponseContent(item));
                continue;
            }

            String role = asText(item.get("role"));
            if ("model".equals(role) && item.containsKey("parts")) {
                contents.add(item);
                continue;
            }

            if ("user".equals(role) || "assistant".equals(role)) {
                String content = flattenContent(item.get("content"));
                if (!content.isBlank()) {
                    contents.add(Map.of(
                            "role", "user".equals(role) ? "user" : "model",
                            "parts", List.of(Map.of("text", content))
                    ));
                }
            }
        }
        return contents;
    }

    private Map<String, Object> createFunctionResponseContent(Map<String, Object> item) {
        String callId = asText(item.get("call_id"));
        String toolName = toolNameByCallId.getOrDefault(callId, "unknown_tool");

        Map<String, Object> functionResponse = new LinkedHashMap<>();
        functionResponse.put("name", toolName);
        functionResponse.put("id", callId);
        functionResponse.put("response", Map.of("output", asText(item.get("output"))));

        return Map.of(
                "role", "user",
                "parts", List.of(Map.of("functionResponse", functionResponse))
        );
    }

    private String flattenContent(Object contentObject) {
        StringBuilder builder = new StringBuilder();
        if (contentObject instanceof List<?>) {
            for (Object contentItem : (List<?>) contentObject) {
                if (!(contentItem instanceof Map<?, ?>)) {
                    continue;
                }

                Map<?, ?> contentMap = (Map<?, ?>) contentItem;
                String contentType = asText(contentMap.get("type"));
                if ("input_text".equals(contentType)) {
                    if (builder.length() > 0) {
                        builder.append("\n");
                    }
                    builder.append(asText(contentMap.get("text")));
                } else if ("input_image".equals(contentType)) {
                    if (builder.length() > 0) {
                        builder.append("\n");
                    }
                    builder.append("[GUI screenshot omitted in Gemini provider]");
                }
            }
        }
        return builder.toString().trim();
    }

    private List<Map<String, Object>> buildGeminiTools() {
        List<Map<String, Object>> functionDeclarations = new ArrayList<>();
        for (Map<String, Object> tool : registeredTools) {
            Map<String, Object> functionDeclaration = new HashMap<>();
            functionDeclaration.put("name", asText(tool.get("name")));
            functionDeclaration.put("description", asText(tool.get("description")));
            functionDeclaration.put("parameters", tool.get("parameters"));
            functionDeclarations.add(functionDeclaration);
        }

        return List.of(Map.of("functionDeclarations", functionDeclarations));
    }

    private void logTokenUsage(Map<?, ?> parsed) {
        Map<?, ?> usageMetadata = (Map<?, ?>) parsed.get("usageMetadata");
        if (usageMetadata == null) {
            lastTokenUsage = 0;
            return;
        }

        Object total = usageMetadata.get("totalTokenCount");
        if (total instanceof Number) {
            lastTokenUsage = ((Number) total).intValue();
        } else {
            lastTokenUsage = 0;
        }
    }

    private LlmProviderResponse toActionResponse(Map<?, ?> parsed) throws LlmProviderException, IOException {
        List<?> candidates = (List<?>) parsed.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new LlmProviderException("Gemini response did not contain candidates.");
        }

        Map<?, ?> firstCandidate = (Map<?, ?>) candidates.get(0);
        Map<?, ?> content = firstCandidate.get("content") instanceof Map<?, ?>
                ? (Map<?, ?>) firstCandidate.get("content")
                : null;
        if (content == null) {
            throw new LlmProviderException("Gemini response did not contain content.");
        }

        List<?> parts = content.get("parts") instanceof List<?>
                ? (List<?>) content.get("parts")
                : Collections.emptyList();

        List<LlmToolCall> toolCalls = new ArrayList<>();
        List<Map<String, Object>> assistantItems = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder();
        List<Map<String, Object>> providerParts = new ArrayList<>();

        for (Object partObject : parts) {
            if (!(partObject instanceof Map<?, ?>)) {
                continue;
            }

            Map<?, ?> partMap = (Map<?, ?>) partObject;
            Map<String, Object> providerPart = new LinkedHashMap<>();

            if (partMap.get("functionCall") instanceof Map<?, ?>) {
                Map<?, ?> functionCall = (Map<?, ?>) partMap.get("functionCall");
                String toolName = asText(functionCall.get("name"));
                String callId = asText(functionCall.get("id"));
                Object args = functionCall.get("args") instanceof Map<?, ?>
                        ? functionCall.get("args")
                        : Collections.emptyMap();
                if (callId.isBlank()) {
                    callId = buildCallId(toolCalls.size(), toolName);
                }
                toolNameByCallId.put(callId, toolName);

                Map<String, Object> providerFunctionCall = new LinkedHashMap<>();
                providerFunctionCall.put("name", toolName);
                providerFunctionCall.put("id", callId);
                providerFunctionCall.put("args", args);
                providerPart.put("functionCall", providerFunctionCall);
                providerParts.add(providerPart);

                toolCalls.add(new LlmToolCall(
                        callId,
                        toolName,
                        objectMapper.writeValueAsString(args)
                ));
            } else {
                String text = asText(partMap.get("text"));
                if (!text.isBlank()) {
                    if (textBuilder.length() > 0) {
                        textBuilder.append("\n");
                    }
                    textBuilder.append(stripFormatting(text));
                }
                providerPart.put("text", text);
                providerParts.add(providerPart);
            }
        }

        if (!providerParts.isEmpty()) {
            assistantItems.add(Map.of(
                    "role", "model",
                    "parts", providerParts
            ));
        }

        return new LlmProviderResponse(
                textBuilder.toString().trim(),
                toolCalls,
                assistantItems
        );
    }

    /**
     * Native function calls are parsed from {@code functionCall} parts, but
     * Gemini may still emit plain assistant text wrapped in markdown fences.
     * This helper normalizes that residual text path.
     */
    private String stripFormatting(String content) {
        String normalized = content != null ? content.trim() : "";
        if (normalized.startsWith("```json")) {
            normalized = normalized.substring(7);
        }
        if (normalized.startsWith("```")) {
            normalized = normalized.substring(3);
        }
        if (normalized.endsWith("```")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return normalized.trim();
    }

    private String buildCallId(int index, String toolName) {
        return "gemini-" + index + "-" + toolName;
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

}
