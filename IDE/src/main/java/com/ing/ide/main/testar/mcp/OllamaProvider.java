package com.ing.ide.main.testar.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Unified Ollama provider for all locally-deployed open-source models.
 *
 * <p>
 * This provider communicates with a local <a href="https://ollama.com">Ollama</a>
 * instance via the {@code /api/chat} endpoint, which supports native tool calling
 * in the OpenAI-compatible format. It handles conversation history internally so
 * the model retains context across the full BDD-step execution loop.
 * </p>
 *
 * <h2>Supported models (tested or documented)</h2>
 * <ul>
 *   <li>{@code llama3.1}       — Meta Llama 3.1 8B, solid baseline, native tool calling</li>
 *   <li>{@code llama3.2}       — Meta Llama 3.2 3B/1B, lightweight, fast on CPU</li>
 *   <li>{@code qwen2.5:7b}     — Alibaba Qwen 2.5 7B, strong structured output</li>
 *   <li>{@code qwen3:8b}       — Alibaba Qwen 3 8B, reasoning + tools</li>
 *   <li>{@code ministral-3:8b} — Mistral Ministral 8B, 128K context, native tool calling</li>
 * </ul>
 *
 * <h2>Architecture</h2>
 * <p>
 * Implements the Strategy Pattern defined by {@link LlmProvider} and is registered
 * in {@link LlmProviderFactory} under the key {@code "Ollama"}. This class replaces
 * the previous split between {@code OllamaProvider} (api/generate) and
 * {@code QwenOllamaProvider} (api/chat), consolidating all local models into a
 * single, more capable implementation.
 * </p>
 *
 * <h2>Tool calling</h2>
 * <p>
 * Tools are passed natively in the {@code tools} field of the {@code /api/chat}
 * request — the same format Ollama uses for OpenAI-compatible function calling.
 * When the model returns a {@code tool_calls} block, this provider serialises it
 * back to the JSON shape that {@link LlmMcpAgent} expects, keeping the upstream
 * pipeline unchanged.
 * </p>
 *
 * @author TFG-MCP-TESTAR Team
 * @version 2.0
 * @since 2025
 * @see LlmProvider
 * @see LlmProviderFactory
 * @see LlmMcpAgent
 */
public class OllamaProvider implements LlmProvider {

    private static final Logger LOGGER = Logger.getLogger(OllamaProvider.class.getName());

    /** Default Ollama /api/chat endpoint for a local instance. */
    private static final String DEFAULT_API_URL = "http://localhost:11434/api/chat";

    /**
     * Default model. ministral-3:8b is Mistral's compact 8B model with 128K context
     * and native tool calling support, available via {@code ollama pull ministral-3:8b}.
     */
    private static final String DEFAULT_MODEL = "ministral-3:8b";

    private final String apiUrl;
    private final String model;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /** Tools registered via {@link #registerTools(List)}, forwarded natively to Ollama. */
    private List<Map<String, Object>> registeredTools;

    /**
     * Conversation history kept in-memory across the BDD execution loop.
     * Allows the model to reference previous steps and tool results without
     * the agent manually injecting the full history string.
     */
    private final List<Map<String, Object>> messagesHistory = new ArrayList<>();

    /** Tracks whether the previous turn ended with a tool_calls response. */
    private List<Map<String, Object>> lastToolCalls = null;

    /** Total tokens consumed in the last {@link #executePrompt} call (prompt + generated). */
    private volatile int lastTokenUsage = 0;

    // ── Constructor ──────────────────────────────────────────────────────────

    /**
     * Creates a new {@code OllamaProvider}.
     *
     * @param apiUrl base URL of the Ollama {@code /api/chat} endpoint;
     *               falls back to {@value #DEFAULT_API_URL} if null or blank
     * @param model  Ollama model tag (e.g. {@code "ministral:8b"}, {@code "qwen2.5:7b"});
     *               falls back to {@value #DEFAULT_MODEL} if null or blank
     */
    public OllamaProvider(String apiUrl, String model) {
        this.apiUrl = (apiUrl != null && !apiUrl.isBlank()) ? apiUrl : DEFAULT_API_URL;
        this.model  = (model  != null && !model.isBlank())  ? model  : DEFAULT_MODEL;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    // ── LlmProvider API ──────────────────────────────────────────────────────

    @Override
    public void registerTools(List<Map<String, Object>> tools) {
        this.registeredTools = tools;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sends a {@code /api/chat} request to Ollama. On the first call the system
     * and user messages are injected; on subsequent calls a new user (or tool-result)
     * message is appended so the model sees the full conversation.</p>
     *
     * <p>If the model responds with a {@code tool_calls} block (native tool invocation),
     * the call is converted to the action JSON format that {@link LlmMcpAgent} expects.
     * Otherwise the plain text content is returned after stripping any markdown fences
     * and model-specific thinking blocks (e.g. Qwen3 {@code <think>…</think>}).</p>
     */
    @Override
    public String executePrompt(String systemPrompt, String userContext) throws LlmProviderException {

        // ── 1. Strip manual history injected by LlmMcpAgent ──────────────────
        // LlmMcpAgent concatenates the full action history for stateless providers.
        // We manage history natively, so we strip that section to avoid exponential
        // context growth across long test runs.
        String cleanUserContext = userContext;
        int histIdx = cleanUserContext.indexOf("\n\nPast Action History:\n");
        if (histIdx != -1) {
            cleanUserContext = cleanUserContext.substring(0, histIdx).trim();
            cleanUserContext += "\n\n(Follow the Pending BDD instructions from your first prompt)";
        }

        // ── 2. Update conversation history ────────────────────────────────────
        if (messagesHistory.isEmpty()) {
            // First call: inject system prompt + initial user context
            messagesHistory.add(Map.of("role", "system", "content", systemPrompt));
            messagesHistory.add(Map.of("role", "user",   "content", cleanUserContext));
        } else {
            // Subsequent calls: append as tool response or regular user message
            if (lastToolCalls != null && !lastToolCalls.isEmpty()) {
                Map<String, Object> toolMsg = new HashMap<>();
                toolMsg.put("role",    "tool");
                toolMsg.put("content", cleanUserContext);
                messagesHistory.add(toolMsg);
            } else {
                messagesHistory.add(Map.of("role", "user", "content", cleanUserContext));
            }
        }

        lastToolCalls = null;

        // ── 3. Build request body ─────────────────────────────────────────────
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model",    this.model);
        requestBody.put("messages", messagesHistory);
        requestBody.put("stream",   false);

        // Pass tools natively — this enables structured tool_calls in the response
        if (registeredTools != null && !registeredTools.isEmpty()) {
            requestBody.put("tools", registeredTools);
        }

        // ── 4. Serialise and send ─────────────────────────────────────────────
        String requestBodyJson;
        try {
            requestBodyJson = objectMapper.writeValueAsString(requestBody);
        } catch (JsonProcessingException e) {
            throw new LlmProviderException("Failed to serialize Ollama request body", e);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(this.apiUrl))
                .timeout(Duration.ofMinutes(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new LlmProviderException("Communication error with Ollama API", e);
        }

        // ── 5. Handle HTTP errors ─────────────────────────────────────────────
        if (response.statusCode() >= 400) {
            LOGGER.log(Level.SEVERE, "Ollama API error. Status: {0}, Body: {1}",
                    new Object[]{ response.statusCode(), response.body() });
            throw new LlmProviderException(
                    "Ollama API error: " + response.statusCode(),
                    response.statusCode(), false, 0);
        }

        // ── 6. Parse the response ─────────────────────────────────────────────
        try {
            Map<?, ?> parsed = objectMapper.readValue(response.body(), Map.class);

            // Collect token counts (Ollama: prompt_eval_count + eval_count = total)
            try {
                Object promptEval = parsed.get("prompt_eval_count");
                Object evalCount  = parsed.get("eval_count");
                int pTokens = (promptEval instanceof Number) ? ((Number) promptEval).intValue() : 0;
                int cTokens = (evalCount  instanceof Number) ? ((Number) evalCount).intValue()  : 0;
                this.lastTokenUsage = pTokens + cTokens;
                if (this.lastTokenUsage > 0) {
                    LOGGER.log(Level.INFO,
                            "[OllamaProvider] Token usage: prompt={0}, completion={1}, total={2}",
                            new Object[]{ pTokens, cTokens, this.lastTokenUsage });
                }
            } catch (Exception ignored) { /* token count is optional */ }

            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) parsed.get("message");

            if (message == null) {
                throw new LlmProviderException("Ollama response missing 'message' field. Body: "
                        + response.body());
            }

            // Always append the raw assistant message to history
            messagesHistory.add(message);

            // 6a. Native tool_calls path ──────────────────────────────────────
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> toolCalls =
                    (List<Map<String, Object>>) message.get("tool_calls");

            if (toolCalls != null && !toolCalls.isEmpty()) {
                this.lastToolCalls = toolCalls;
                Map<String, Object> firstCall = toolCalls.get(0);
                Map<?, ?> function = (Map<?, ?>) firstCall.get("function");

                if (function != null) {
                    String name      = (String) function.get("name");
                    Object arguments = function.get("arguments");

                    // Re-shape to the action JSON format LlmMcpAgent expects:
                    // { "thought": "...", "action": { "toolName": "...", "parameters": {...} } }
                    Map<String, Object> actionObj = new HashMap<>();
                    actionObj.put("toolName",   name);
                    actionObj.put("parameters", arguments instanceof Map ? arguments : new HashMap<>());

                    Map<String, Object> agentJson = new HashMap<>();
                    agentJson.put("thought", "Calling tool: " + name);
                    agentJson.put("action",  actionObj);

                    String result = objectMapper.writeValueAsString(agentJson);
                    LOGGER.log(Level.INFO,
                            "[OllamaProvider] Native tool_call → model={0}, tool={1}",
                            new Object[]{ this.model, name });
                    return result;
                }
            }

            // 6b. Plain text / JSON content path ──────────────────────────────
            String content = (String) message.get("content");
            if (content == null || content.isBlank()) {
                throw new LlmProviderException(
                        "Ollama returned empty content and no tool_calls. Body: " + response.body());
            }

            // Strip markdown code fences (```json … ```)
            content = content.trim();
            if (content.startsWith("```json")) content = content.substring(7);
            if (content.startsWith("```"))     content = content.substring(3);
            if (content.endsWith("```"))       content = content.substring(0, content.length() - 3);

            // Strip model-specific thinking blocks (Qwen3: <think>…</think>)
            content = stripThinkingBlock(content.trim());

            LOGGER.log(Level.INFO,
                    "[OllamaProvider] Content response (model={0}, first 200 chars): {1}",
                    new Object[]{ this.model, content.substring(0, Math.min(200, content.length())) });

            return content.trim();

        } catch (JsonProcessingException e) {
            throw new LlmProviderException("Failed to parse Ollama JSON response", e);
        }
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public int getLastTokenUsage() {
        return lastTokenUsage;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@code true} for known vision-capable Ollama models:
     * llava variants, Qwen-VL, and Llama 3.2 Vision.</p>
     */
    @Override
    public boolean supportsVision() {
        String m = model.toLowerCase();
        return m.contains("llava")
                || m.contains("-vl")
                || (m.contains("llama3.2") && m.contains("vision"));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@code true} for models that emit {@code <think>} blocks or
     * expose a reasoning effort parameter (currently Qwen3 series).</p>
     */
    @Override
    public boolean isReasoningModel() {
        return model.toLowerCase().startsWith("qwen3");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Removes model-specific reasoning/thinking blocks that appear before the
     * actual JSON response.
     *
     * <ul>
     *   <li>Qwen3: {@code <think>…</think>}</li>
     * </ul>
     *
     * @param text raw model output
     * @return text with thinking blocks removed, or the original text if no
     *         block is found (guaranteeing we never return an empty string when
     *         the model produced useful output)
     */
    private String stripThinkingBlock(String text) {
        if (text == null) return "";
        String stripped = text.replaceAll("(?s)<think>.*?</think>", "").trim();
        return stripped.isEmpty() ? text : stripped;
    }
}
