package com.ing.ide.main.testar.mcp;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gemini Provider for the Multi-LLM test architecture.
 */
public class GeminiProvider implements LlmProvider {

    private static final Logger LOGGER = Logger.getLogger(GeminiProvider.class.getName());

    private final String apiUrl;
    private final String apiKey;
    private final String model;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private List<Map<String, Object>> registeredTools;

    /** Total tokens consumed in the last {@link #executePrompt} call. */
    private volatile int lastTokenUsage = 0;

    public GeminiProvider(String apiUrl, String apiKey, String model) {
        if (apiUrl == null || apiUrl.isBlank()) {
            this.apiUrl = "https://generativelanguage.googleapis.com/v1beta/models/"
                    + (model != null && !model.isBlank() ? model : "gemini-2.5-flash") + ":generateContent";
        } else {
            this.apiUrl = apiUrl;
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API Key cannot be null or empty for Gemini");
        }

        this.apiKey = apiKey;
        this.model = model != null && !model.isBlank() ? model : "gemini-2.5-flash";

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String executePrompt(String systemPrompt, String userContext) throws LlmProviderException {
        try {
            String fullPrompt = systemPrompt + "\n\nUser Context:\n" + userContext;

            if (registeredTools != null && !registeredTools.isEmpty()) {
                String toolsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(registeredTools);
                fullPrompt += "\n\nAVAILABLE TOOLS:\n" + toolsJson;
            }

            Map<String, Object> requestBody = new HashMap<>();

            List<Map<String, Object>> parts = new ArrayList<>();
            parts.add(Map.of("text", fullPrompt));

            Map<String, Object> contentMap = new HashMap<>();
            contentMap.put("parts", parts);
            contentMap.put("role", "user");

            requestBody.put("contents", List.of(contentMap));

            // Force JSON response formatting
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("response_mime_type", "application/json");
            requestBody.put("generationConfig", generationConfig);

            String requestBodyJson = objectMapper.writeValueAsString(requestBody);

            String encodedKey = URLEncoder.encode(this.apiKey, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(this.apiUrl + "?key=" + encodedKey))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                LOGGER.log(Level.SEVERE, "Gemini API Error. Status: {0}, Body: {1}",
                        new Object[] { response.statusCode(), response.body() });
                throw new LlmProviderException("Gemini API Error: " + response.statusCode(), response.statusCode(),
                        false, 0);
            }

            // Extract the generated text
            Map<?, ?> responseMap = objectMapper.readValue(response.body(), Map.class);

            // Parse token usage from usageMetadata (available in all Gemini responses)
            try {
                Map<?, ?> usageMeta = (Map<?, ?>) responseMap.get("usageMetadata");
                if (usageMeta != null) {
                    Object total = usageMeta.get("totalTokenCount");
                    if (total instanceof Number) {
                        this.lastTokenUsage = ((Number) total).intValue();
                        LOGGER.log(Level.INFO, "Gemini token usage: total={0}", this.lastTokenUsage);
                    }
                }
            } catch (Exception ignored) { /* token count is optional */ }

            List<?> candidates = (List<?>) responseMap.get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<?, ?> firstCandidate = (Map<?, ?>) candidates.get(0);
                Map<?, ?> content = (Map<?, ?>) firstCandidate.get("content");
                List<?> responseParts = (List<?>) content.get("parts");
                if (responseParts != null && !responseParts.isEmpty()) {
                    Map<?, ?> firstPart = (Map<?, ?>) responseParts.get(0);
                    String text = (String) firstPart.get("text");

                    if (text != null) {
                        text = text.trim();
                        if (text.startsWith("```json")) {
                            text = text.substring(7);
                        }
                        if (text.endsWith("```")) {
                            text = text.substring(0, text.length() - 3);
                        }
                        return text.trim();
                    }
                }
            }

            throw new LlmProviderException("Could not extract response from Gemini API");

        } catch (IOException | InterruptedException e) {
            throw new LlmProviderException("Communication error with Gemini API", e);
        }
    }

    @Override
    public int getLastTokenUsage() {
        return lastTokenUsage;
    }

    @Override
    public void registerTools(List<Map<String, Object>> tools) {
        this.registeredTools = tools;
    }

    @Override
    public String getModelName() {
        return model;
    }

    @Override
    public boolean supportsVision() {
        return model.contains("flash") || model.contains("pro");
    }

    @Override
    public boolean isReasoningModel() {
        return model.contains("thinking");
    }
}
