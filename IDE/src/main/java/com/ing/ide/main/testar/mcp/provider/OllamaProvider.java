package com.ing.ide.main.testar.mcp.provider;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Ollama implementation of the {@link LlmProvider} contract.
 *
 * <p>
 * This provider uses Ollama's native tool-calling support through the
 * {@code /api/chat} endpoint. It adapts the MCP tool schema to the
 * Ollama function-tool format, parses native {@code message.tool_calls},
 * and sends tool outputs back as {@code role="tool"} messages.
 * </p>
 */
public class OllamaProvider implements LlmProvider {

    private static final Logger LOGGER = Logger.getLogger(OllamaProvider.class.getName());

    private final String apiUrl;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private List<Map<String, Object>> registeredTools = Collections.emptyList();
    private final Map<String, String> toolNameByCallId = new HashMap<>();
    private volatile int lastTokenUsage = 0;

    public OllamaProvider(String apiUrl, String model) {
        this.apiUrl = apiUrl != null && !apiUrl.isBlank()
                ? apiUrl
                : "http://localhost:11434/api/chat";
        this.model = model != null && !model.isBlank() ? model : "qwen3:8b";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Executes one provider turn using the Ollama chat endpoint.
     */
    @Override
    public LlmProviderResponse executePrompt(String systemPrompt,
                                             List<Map<String, Object>> input,
                                             boolean visionRequested) throws LlmProviderException {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("stream", false);
            requestBody.put("messages", buildMessages(systemPrompt, input));
            if (!registeredTools.isEmpty()) {
                requestBody.put("tools", buildOllamaTools());
            }

            String requestJson = objectMapper.writeValueAsString(requestBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMinutes(10))
                    .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                LOGGER.log(Level.SEVERE, "Ollama API error. Status: {0}, Body: {1}",
                        new Object[] { response.statusCode(), response.body() });
                throw new LlmProviderException("Ollama API error: " + response.statusCode(),
                        response.statusCode(), false, 0L);
            }

            Map<?, ?> parsed = objectMapper.readValue(response.body(), Map.class);
            logTokenUsage(parsed);

            Map<?, ?> message = parsed.get("message") instanceof Map<?, ?>
                    ? (Map<?, ?>) parsed.get("message")
                    : null;

            return toActionResponse(message);
        } catch (LlmProviderException exception) {
            throw exception;
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new LlmProviderException("Communication error with Ollama API", exception);
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
        return normalizedModel.contains("qwen3") || normalizedModel.contains("reason");
    }

    @Override
    public int getLastTokenUsage() {
        return lastTokenUsage;
    }

    private List<Map<String, Object>> buildMessages(String systemPrompt, List<Map<String, Object>> input) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));

        for (Map<String, Object> item : input) {
            String type = asText(item.get("type"));
            if ("function_call_output".equals(type)) {
                messages.add(createToolMessage(item));
                continue;
            }

            String role = asText(item.get("role"));
            if ("assistant".equals(role) && item.containsKey("tool_calls")) {
                messages.add(item);
                continue;
            }

            if ("user".equals(role) || "assistant".equals(role)) {
                String content = flattenContent(item.get("content"));
                if (!content.isBlank()) {
                    messages.add(Map.of("role", role, "content", content));
                }
            }
        }

        return messages;
    }

    private Map<String, Object> createToolMessage(Map<String, Object> item) {
        String callId = asText(item.get("call_id"));
        String toolName = toolNameByCallId.getOrDefault(callId, "unknown_tool");
        return Map.of(
                "role", "tool",
                "tool_name", toolName,
                "content", asText(item.get("output"))
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
                    builder.append("[GUI screenshot omitted in Ollama provider]");
                }
            }
        }
        return builder.toString().trim();
    }

    private List<Map<String, Object>> buildOllamaTools() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (Map<String, Object> tool : registeredTools) {
            Map<String, Object> function = new HashMap<>();
            function.put("name", asText(tool.get("name")));
            function.put("description", asText(tool.get("description")));
            function.put("parameters", tool.get("parameters"));

            Map<String, Object> ollamaTool = new HashMap<>();
            ollamaTool.put("type", "function");
            ollamaTool.put("function", function);
            tools.add(ollamaTool);
        }
        return tools;
    }

    private LlmProviderResponse toActionResponse(Map<?, ?> message) throws IOException {
        if (message == null) {
            return new LlmProviderResponse("", Collections.emptyList(), Collections.emptyList());
        }

        List<LlmToolCall> toolCalls = new ArrayList<>();
        Object toolCallsObject = message.get("tool_calls");
        if (toolCallsObject instanceof List<?>) {
            int index = 0;
            for (Object toolCallObject : (List<?>) toolCallsObject) {
                if (!(toolCallObject instanceof Map<?, ?>)) {
                    continue;
                }

                Map<?, ?> toolCallMap = (Map<?, ?>) toolCallObject;
                Map<?, ?> function = toolCallMap.get("function") instanceof Map<?, ?>
                        ? (Map<?, ?>) toolCallMap.get("function")
                        : Collections.emptyMap();
                String toolName = asText(function.get("name"));
                Object argumentsObject = function.get("arguments") instanceof Map<?, ?>
                        ? function.get("arguments")
                        : Collections.emptyMap();
                String callId = buildCallId(index, toolName);
                toolNameByCallId.put(callId, toolName);

                toolCalls.add(new LlmToolCall(
                        callId,
                        toolName,
                        objectMapper.writeValueAsString(argumentsObject)
                ));
                index++;
            }
        }

        Map<String, Object> assistantMessage = new HashMap<>();
        assistantMessage.put("role", "assistant");
        String content = stripFormatting(asText(message.get("content")));
        if (!content.isBlank()) {
            assistantMessage.put("content", content);
        }
        if (toolCallsObject instanceof List<?>) {
            assistantMessage.put("tool_calls", toolCallsObject);
        }

        return new LlmProviderResponse(
                content,
                toolCalls,
                List.of(assistantMessage)
        );
    }

    private void logTokenUsage(Map<?, ?> parsed) {
        int promptTokens = parsed.get("prompt_eval_count") instanceof Number
                ? ((Number) parsed.get("prompt_eval_count")).intValue()
                : 0;
        int completionTokens = parsed.get("eval_count") instanceof Number
                ? ((Number) parsed.get("eval_count")).intValue()
                : 0;
        lastTokenUsage = promptTokens + completionTokens;
    }

    /**
     * Native tool calls are parsed from {@code message.tool_calls}, but Ollama
     * models may still emit plain assistant content wrapped in markdown fences
     * or thinking blocks. This helper normalizes that residual text path.
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

        int thinkStart = normalized.indexOf("<think>");
        int thinkEnd = normalized.indexOf("</think>");
        if (thinkStart != -1 && thinkEnd > thinkStart) {
            normalized = normalized.substring(thinkEnd + "</think>".length()).trim();
        }
        return normalized.trim();
    }

    private String buildCallId(int index, String toolName) {
        return "ollama-" + index + "-" + toolName;
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

}
