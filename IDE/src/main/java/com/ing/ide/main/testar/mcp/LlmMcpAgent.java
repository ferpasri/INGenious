package com.ing.ide.main.testar.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ing.datalib.component.Project;
import com.ing.ide.main.testar.mcp.helper.McpNames;
import com.ing.ide.main.testar.mcp.helper.McpToolBuilder;
import com.ing.ide.main.testar.mcp.helper.McpToolExecutor;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LLM-based Model Context Protocol (MCP) Agent for BDD-GUI testing.
 * 
 * <p>
 * This class orchestrates the interaction between a Large Language Model (LLM)
 * and the MCP interface to execute BDD (Behavior-Driven Development) test
 * scenarios
 * on web applications using GUI automation.
 * </p>
 * 
 * <h2>Multi-LLM Architecture</h2>
 * <p>
 * This class is part of a <strong>Strategy Pattern</strong> implementation that
 * supports
 * multiple LLM providers for the following business reasons:
 * </p>
 * <ul>
 * <li><strong>GDPR Compliance</strong>: Switch to privacy-focused or on-premise
 * models</li>
 * <li><strong>Cost Optimization</strong>: Use different providers based on
 * pricing</li>
 * <li><strong>Flexibility</strong>: Easy integration of new LLM providers</li>
 * </ul>
 * 
 * <p>
 * The LLM provider is abstracted via the {@link LlmProvider} interface,
 * allowing
 * seamless switching between OpenAI, Google Gemini, Meta Llama, or any other
 * provider
 * without modifying this class.
 * </p>
 * 
 * <h2>Supported Providers</h2>
 * <ul>
 * <li>{@link OpenAiProvider} - OpenAI and Azure OpenAI</li>
 * <li>{@link GeminiProvider} - Google Gemini</li>
 * <li>{@link OllamaProvider} - Modelos locales via Ollama (Llama, Qwen, Mistral, etc.)</li>
 * </ul>
 * 
 * @author TFG-MCP-TESTAR Team
 * @version 2.0
 * @since 2024
 * @see LlmProvider
 * @see OpenAiProvider
 * @see McpInterface
 */
public class LlmMcpAgent {

    private static final Logger LOGGER = Logger.getLogger(LlmMcpAgent.class.getName());

    private final ObjectMapper mapper = new ObjectMapper();

    /** The LLM provider implementing the Strategy pattern */
    private final LlmProvider aiProvider;

    private final int maxActions;
    private final String bddInstructions;
    private final McpInterface mcpInterface;

    // ── Métricas de ejecución para el dashboard de resultados ────────────────
    /** Latencia en ms de cada llamada al LLM (una entrada por paso). RQ2 */
    private final List<Long> latencyTimeline = new ArrayList<>();
    /** Acciones inválidas / alucinaciones detectadas en este run. RQ4 */
    private int invalidActions = 0;
    /** true cuando el agente llama a stopTestExecution con éxito. RQ1 */
    private boolean completedSuccess = false;
    /** Total de tokens consumidos en este run (suma de todas las llamadas). RQ3 */
    private int totalTokens = 0;

    /**
     * Constructs a new LlmMcpAgent with the specified configuration.
     * 
     * <p>
     * This constructor creates an {@link OpenAiProvider} as the default LLM
     * provider.
     * To use a different provider, use the alternative constructor that accepts
     * a {@link LlmProvider} instance.
     * </p>
     * 
     * @param project              the INGenious project context
     * @param openaiApiUrl         the OpenAI API endpoint URL
     * @param openaiApiKeyVariable the name of the environment variable containing
     *                             the API key
     * @param openaiModel          the model identifier (e.g., "gpt-4o")
     * @param vision               whether vision/image input is enabled
     * @param reasoningLevel       the reasoning effort level for reasoning models
     * @param maxActions           maximum number of LLM actions before stopping
     * @param bddInstructions      the BDD instructions to execute
     * @throws IllegalStateException if the API key environment variable is not set
     */
    public LlmMcpAgent(Project project,
            String openaiApiUrl,
            String openaiApiKeyVariable,
            String openaiModel,
            boolean vision,
            String reasoningLevel,
            int maxActions,
            String bddInstructions) {

        String apiKey = System.getenv(openaiApiKeyVariable);
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                    "Environment variable '" + openaiApiKeyVariable + "' is not set or is empty.");
        }

        // Initialize the LLM provider using the Strategy pattern
        this.aiProvider = new OpenAiProvider(
                openaiApiUrl,
                apiKey,
                openaiModel,
                vision,
                reasoningLevel);

        this.maxActions = maxActions;
        this.bddInstructions = bddInstructions;

        // Wrap the technical MCP driver with the BDD steps validator
        PlaywrightMcpDriver mcpDriver = new PlaywrightMcpDriver(project, bddInstructions);
        this.mcpInterface = new BddMcpValidator(mcpDriver, new BddStepTracker(bddInstructions));
    }

    /**
     * Constructs a new LlmMcpAgent with a custom LLM provider.
     * 
     * <p>
     * This constructor allows injecting any {@link LlmProvider} implementation,
     * enabling support for different LLM backends (Gemini, Llama, etc.).
     * </p>
     * 
     * @param project         the INGenious project context
     * @param aiProvider      the LLM provider to use
     * @param maxActions      maximum number of LLM actions before stopping
     * @param bddInstructions the BDD instructions to execute
     */
    public LlmMcpAgent(Project project,
            LlmProvider aiProvider,
            int maxActions,
            String bddInstructions) {

        this.aiProvider = aiProvider;
        this.maxActions = maxActions;
        this.bddInstructions = bddInstructions;

        // Wrap the technical MCP driver with the BDD steps validator
        PlaywrightMcpDriver mcpDriver = new PlaywrightMcpDriver(project, bddInstructions);
        this.mcpInterface = new BddMcpValidator(mcpDriver, new BddStepTracker(bddInstructions));
    }

    /**
     * Executes the LLM agent loop to complete the BDD test scenario.
     * 
     * <p>
     * The agent iteratively:
     * </p>
     * <ol>
     * <li>Sends the current conversation state to the LLM</li>
     * <li>Receives tool calls from the LLM</li>
     * <li>Executes the requested tools via {@link McpInterface}</li>
     * <li>Adds tool results to the conversation</li>
     * <li>Repeats until completion or maxActions reached</li>
     * </ol>
     * 
     * @return a status message indicating how the agent terminated
     */
    public String runLLMAgent() {
        // Register the available tools with the LLM provider
        final List<Map<String, Object>> tools = McpToolBuilder.from(McpInterface.class);
        aiProvider.registerTools(tools);

        final McpToolExecutor<McpInterface> executor = McpToolExecutor.of(McpInterface.class, mcpInterface, mapper);

        // This is the "Split + Verbose" strategy base prompt
        final String baseSystemPrompt = "You are a strict BDD-GUI test strategy agent for INGenious MCP.\n" +
                "You must analyze the User Context (DOM state) to perform actions on the GUI step by step.\n" +
                "Your objective is to complete the BDD instructions provided.\n\n" +
                "CRITICAL INSTRUCTION 1 - MULTI-ACTION BDD STEPS:\n" +
                "A single BDD step may require MULTIPLE GUI interactions (e.g. click a radio AND fill an input). " +
                "Verify that ALL logical UI components for the current BDD step are fully executed in the DOM before advancing to the next BDD step. " +
                "Do NOT skip ahead just because you executed one action for a step.\n\n" +
                "CRITICAL INSTRUCTION 2 - SPLIT + VERBOSE STRATEGY:\n" +
                "You MUST return your response ONLY as a valid JSON object raw (no markdown backticks). " +
                "Your JSON MUST contain exactly two top-level keys: \"thought\" and \"action\".\n" +
                "- \"thought\": A verbose explanation of your analysis of the current GUI state, why you chose the web element, and how it aligns with the current BDD step. Mention explicitly if the step requires more actions down the line.\n"
                +
                "- \"action\": A JSON object describing the tool to execute. It must contain \"toolName\" (string) and \"parameters\" (an object with key-value pairs matching the exact arguments for the chosen tool).\n\n"
                +
                "Example Format:\n" +
                "{\n" +
                "  \"thought\": \"I am observing the DOM. I see the 'Login' button with CSS selector '#login'. Since the BDD says 'When the user logs in', I will click this button.\",\n"
                +
                "  \"action\": {\n" +
                "    \"toolName\": \"executeClickAction\",\n" +
                "    \"parameters\": {\n" +
                "      \"bddStep\": \"When the user logs in\",\n" +
                "      \"rawCssSelector\": \"#login\"\n" +
                "    }\n" +
                "  }\n" +
                "}\n\n" +
                "Use ONLY the tools provided to you. If you complete all asserts or test execution must end, use the stopTestExecution tool. Begin!";

        int step = 0;
        int consecutiveFailures = 0;   // circuit-breaker: salida si el modelo falla N veces seguidas
        final int MAX_CONSECUTIVE_FAILURES = 5;
        List<String> actionHistory = new ArrayList<>();

        // At the beginning, the context is the BDD list itself
        String currentUserContext = "Initial state. BDD Instructions to accomplish:\n" + this.bddInstructions
                + "\nStart by loading the URL.";

        while (step < this.maxActions) {
            try {
                addInfoLog("\n--- INFERENCING STEP: " + step + " ---");

                // Track Latency for RQ3 metric
                long startTime = System.currentTimeMillis();

                // Call the LLM provider (Strategy pattern)
                String jsonResponse = aiProvider.executePrompt(baseSystemPrompt, currentUserContext);

                long endTime = System.currentTimeMillis();
                long latencyMs = endTime - startTime;

                addInfoLog(String.format("LLM Inference Latency: %d ms (Provider: %s)", latencyMs,
                        aiProvider.getModelName()));
                latencyTimeline.add(latencyMs); // RQ2: acumula latencia por paso

                // RQ3: acumula tokens consumidos en este paso
                int stepTokens = aiProvider.getLastTokenUsage();
                if (stepTokens > 0) {
                    totalTokens += stepTokens;
                    addInfoLog(String.format("Token usage this step: %d | Total acumulado: %d", stepTokens, totalTokens));
                }

                // Parse the response based on the Split + Verbose schema.
                // Pre-process: strip JS-style single-line comments (// ...) that some local
                // models (Qwen, Mistral) inject inside the JSON, making it invalid.
                String cleanedResponse = stripJsComments(jsonResponse);

                Map<?, ?> parsedResponse;
                try {
                    parsedResponse = mapper.readValue(cleanedResponse, Map.class);
                    consecutiveFailures = 0; // reset on successful parse
                } catch (JsonProcessingException e) {
                    addSevereLog("The LLM failed to return a proper JSON. Attempting recovery...");
                    addSevereLog("Raw Output: " + jsonResponse);
                    invalidActions++; // RQ4: JSON malformado = alucinación de formato
                    consecutiveFailures++;
                    step++; // FIX: avanzar el contador para no quedarse bloqueado en el mismo step
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        addSevereLog("Circuit-breaker: " + MAX_CONSECUTIVE_FAILURES
                                + " fallos consecutivos de parseo JSON. Abortando run.");
                        writeMetricsJs(step);
                        return "Aborted: too many consecutive JSON parse failures";
                    }
                    currentUserContext = "ERROR: Your last response was not valid JSON. "
                            + "Do NOT add any comments (// or /* */) inside the JSON — JSON does not support comments. "
                            + "Return ONLY a raw JSON object with 'thought' and 'action' keys, no markdown, no comments.";
                    continue;
                }

                String thought = (String) parsedResponse.get("thought");

                // Llama 3.2 sometimes double-encodes "action" as a JSON string instead of an object
                Map<?, ?> action;
                Object rawAction = parsedResponse.get("action");
                if (rawAction instanceof Map) {
                    action = (Map<?, ?>) rawAction;
                } else if (rawAction instanceof String) {
                    addSevereLog("WARNING: 'action' field was a JSON string (double-encoded). Re-parsing...");
                    try {
                        action = mapper.readValue((String) rawAction, Map.class);
                    } catch (JsonProcessingException e) {
                        addSevereLog("Failed to re-parse action string: " + rawAction);
                        currentUserContext = "ERROR: Your 'action' field must be a JSON object, NOT a string. Return it as a nested JSON object. Try again.";
                        continue;
                    }
                } else {
                    action = null;
                }

                if (action == null || !action.containsKey("toolName")) {
                    String feedback = "ISSUE: No 'action' block or 'toolName' provided. Please review.";
                    addSevereLog(feedback);
                    invalidActions++; // RQ4: acción inválida / alucinación estructural
                    currentUserContext = "Error: Invalid JSON schema. 'action' block missing or 'toolName' empty. Follow strictly the format.";
                    continue;
                }

                String toolName = (String) action.get("toolName");
                Map<?, ?> paramsMap = (Map<?, ?>) action.get("parameters");
                String argumentsJson = mapper.writeValueAsString(paramsMap != null ? paramsMap : new HashMap<>());

                addInfoLog("THOUGHT: " + thought);
                addInfoLog("ACTION: " + toolName);
                addInfoLog("PARAMETERS: " + argumentsJson);

                Object resultObj = executor.execute(toolName, argumentsJson);
                String result = (resultObj == null) ? "null" : resultObj.toString();

                if (McpNames.of(McpInterface::stopTestExecution).equals(toolName)) {
                    addInfoLog("LLM agent decided to stop the test execution.");
                    completedSuccess = true; // RQ1: el agente completó el objetivo
                    writeMetricsJs(step + 1);
                    return "LLM agent decided to stop the test execution";
                }

                boolean requireStateImage = McpNames.of(McpInterface::getStateImage).equals(toolName);

                if (requireStateImage && !result.isEmpty() && aiProvider.supportsVision()) {
                    addInfoLog("VISION REQUEST: Model supports vision, obtaining image frame...");
                    currentUserContext = "Action executing " + toolName
                            + " resulted in: [Base64 Image Attached]. Use the returned DOM and state interactive widgets to continue the BDD.";
                } else {
                    addInfoLog("DEBUG tool result: " + result);
                    // Next user text will be the outcome of the tool or the DOM state
                    currentUserContext = "Result of the previous action '" + toolName + "':\n" + result
                            + "\nAnalyze this consequence and plan your next action to follow the BDD.";
                }

                actionHistory.add("Step " + step + " [THOUGHT: " + thought + "] -> ACTION: " + toolName + " " + argumentsJson);
                // Append action history and general BDD instructions reminder to the context loop
                currentUserContext += "\n\nPast Action History:\n" + String.join("\n", actionHistory);
                currentUserContext += "\n\nPending BDD:\n" + this.bddInstructions;

                consecutiveFailures = 0; // acción ejecutada con éxito — reset del circuit-breaker
                step++;

            } catch (LlmProviderException e) {
                if (e.isRateLimited()) {
                    long waitTime = e.getRetryAfterMs();
                    addInfoLog("Rate limited... waiting " + (waitTime / 1000) + " seconds...");
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return "Execution interrupted";
                    }
                } else {
                    addSevereLog("LLM provider error: " + e.getMessage());
                    // Si el error es 403 (acceso denegado al proyecto) no registramos el run —
                    // es un error de configuración/API, no un resultado experimental válido.
                    if (e.getHttpStatusCode() == 403) {
                        addSevereLog("Run descartado de métricas: error 403 (acceso denegado al proyecto API). Corrige la API key o los permisos del proyecto y reintenta.");
                    } else {
                        writeMetricsJs(step);
                    }
                    return "Stop execution due to LLM provider error: " + e.getHttpStatusCode();
                }
            } catch (Exception e) {
                addSevereLog("LLM step failed: " + e.getMessage());
                e.printStackTrace();

                // FIX: detectar TargetClosedError de Playwright (navegador cerrado inesperadamente).
                // Si el browser ya está cerrado es imposible continuar — abortamos el run limpiamente
                // en lugar de entrar en un bucle infinito intentando usar una página muerta.
                String exClass = e.getClass().getSimpleName();
                String cause   = (e.getCause() != null) ? e.getCause().getClass().getSimpleName() : "";
                if ("TargetClosedError".equals(exClass) || "TargetClosedError".equals(cause)
                        || (e.getMessage() != null && e.getMessage().contains("Target page")
                            && e.getMessage().contains("closed"))) {
                    addSevereLog("FATAL: El navegador Playwright se cerró inesperadamente. Abortando run.");
                    writeMetricsJs(step);
                    return "Aborted: Playwright browser closed unexpectedly (TargetClosedError)";
                }

                // Para cualquier otro error: incrementar step y circuit-breaker
                // para evitar bucles infinitos en casos inesperados.
                consecutiveFailures++;
                step++;
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    addSevereLog("Circuit-breaker: " + MAX_CONSECUTIVE_FAILURES
                            + " errores consecutivos. Abortando run.");
                    writeMetricsJs(step);
                    return "Aborted: too many consecutive step failures";
                }
                currentUserContext = "ERROR in previous step: " + e.getMessage()
                        + "\nReview your last action and try a different approach.";
            }
        }

        mcpInterface.stopTestExecution();
        addInfoLog("maxAction executed");
        writeMetricsJs(step);
        return "maxAction executed";
    }

    // ── Métricas: escritura de mcp_metrics.js ──────────────────────────────────

    /**
     * Escribe (o actualiza) mcp_metrics.js en el directorio de trabajo.
     * Acumula los resultados de todos los runs en mcp_all_runs.json para
     * poder comparar modelos en el dashboard después de varias ejecuciones.
     *
     * @param totalSteps número de pasos que ejecutó el agente en este run
     */
    @SuppressWarnings("unchecked")
    private void writeMetricsJs(int totalSteps) {
        try {
            // Si el bat exporta MCP_DATA_DIR, los datos van a experiment_data\ (estable entre builds).
            // Si no, caen al directorio de trabajo (compatibilidad con ejecuciones directas).
            String dataDir = System.getenv("MCP_DATA_DIR");
            String basePath = (dataDir != null && !dataDir.isEmpty()) ? dataDir + java.io.File.separator : "";

            java.io.File accFile = new java.io.File(basePath + "mcp_all_runs.json");

            // 1. Cargar runs previos (si existen)
            List<Map<String, Object>> allRuns;
            if (accFile.exists()) {
                allRuns = mapper.readValue(accFile,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            } else {
                allRuns = new ArrayList<>();
            }

            // 2. Construir entrada del run actual
            Map<String, Object> run = new java.util.LinkedHashMap<>();
            run.put("runId",          "run-" + (allRuns.size() + 1));
            run.put("timestamp",      new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
                                          .format(new java.util.Date()));
            run.put("model",          aiProvider.getModelName());
            // Guardar el BDD completo para que el dashboard distinga H1/H2/H3
            run.put("testGoal",       this.bddInstructions.trim());
            run.put("success",        completedSuccess);
            run.put("totalSteps",     totalSteps);
            run.put("invalidActions", invalidActions);
            run.put("totalTokens",    totalTokens);
            long avgLat = latencyTimeline.isEmpty() ? 0L
                : latencyTimeline.stream().mapToLong(l -> l).sum() / latencyTimeline.size();
            run.put("avgLatencyMs",   avgLat);
            run.put("latencyTimeline", latencyTimeline);
            allRuns.add(run);

            // 3. Persistir JSON acumulado (escritura atómica: tmp → rename)
            java.io.File tmpFile = new java.io.File(accFile.getParent(), "mcp_all_runs.json.tmp");
            mapper.writeValue(tmpFile, allRuns);
            if (accFile.exists()) accFile.delete();
            tmpFile.renameTo(accFile);

            // 4. Agregar por modelo para las gráficas comparativas
            Map<String, List<Map<String, Object>>> byModel = new java.util.LinkedHashMap<>();
            for (Map<String, Object> r : allRuns) {
                String m = (String) r.get("model");
                byModel.computeIfAbsent(m, k -> new ArrayList<>()).add(r);
            }

            List<Map<String, Object>> models = new ArrayList<>();
            for (Map.Entry<String, List<Map<String, Object>>> entry : byModel.entrySet()) {
                List<Map<String, Object>> mrs = entry.getValue();
                int rCount  = mrs.size();
                long sucCnt = mrs.stream().filter(r -> Boolean.TRUE.equals(r.get("success"))).count();
                double aSteps = mrs.stream().mapToInt(r -> ((Number) r.get("totalSteps")).intValue()).average().orElse(0);
                double aLat    = mrs.stream().mapToLong(r -> ((Number) r.get("avgLatencyMs")).longValue()).average().orElse(0);
                double aHall   = mrs.stream().mapToInt(r -> ((Number) r.get("invalidActions")).intValue()).average().orElse(0);
                double aTokens = mrs.stream().mapToInt(r -> {
                    Object t = r.get("totalTokens");
                    return (t instanceof Number) ? ((Number) t).intValue() : 0;
                }).average().orElse(0);

                Map<String, Object> mMap = new java.util.LinkedHashMap<>();
                mMap.put("name",              entry.getKey());
                mMap.put("type",              inferModelType(entry.getKey()));
                mMap.put("runs",              rCount);
                mMap.put("successRate",       Math.round(100.0 * sucCnt / rCount));
                mMap.put("avgSteps",          (long) Math.round(aSteps));
                mMap.put("avgLatencyMs",      (long) Math.round(aLat));
                mMap.put("avgHallucinations", Math.round(aHall * 10.0) / 10.0);
                mMap.put("avgTokens",         (long) Math.round(aTokens));
                models.add(mMap);
            }

            // 5. Metadata del run actual
            Map<String, Object> meta = new java.util.LinkedHashMap<>();
            meta.put("lastRunId",   run.get("runId"));
            meta.put("timestamp",   run.get("timestamp"));
            meta.put("lastRunGoal", run.get("testGoal"));
            meta.put("totalRuns",   allRuns.size());

            Map<String, Object> dashData = new java.util.LinkedHashMap<>();
            dashData.put("meta",            meta);
            dashData.put("models",          models);
            dashData.put("latencyTimeline", latencyTimeline);
            dashData.put("runs",            allRuns);

            // 6. Escribir mcp_metrics.js en la ruta estable y también en el working dir
            //    (el dashboard del run necesita el .js en la misma carpeta que él)
            String js = "var mcpMetricsData = " + mapper.writeValueAsString(dashData) + ";";
            byte[] jsBytes = js.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            java.nio.file.Files.write(java.nio.file.Paths.get(basePath + "mcp_metrics.js"), jsBytes);
            if (!basePath.isEmpty()) {
                // También en el working dir para que HtmlSummaryHandler lo copie al resultado
                java.nio.file.Files.write(java.nio.file.Paths.get("mcp_metrics.js"), jsBytes);
            }

            addInfoLog(String.format(
                "✅ mcp_metrics.js actualizado · %d run(s) acumulados · modelo: %s · éxito: %b · alucinaciones: %d",
                allRuns.size(), aiProvider.getModelName(), completedSuccess, invalidActions));

        } catch (Exception e) {
            addSevereLog("Error al escribir mcp_metrics.js: " + e.getMessage());
        }
    }

    /**
     * Infiere el tipo de despliegue del modelo a partir de su nombre.
     * Usado por el dashboard para colorear y agrupar los resultados.
     */
    private String inferModelType(String modelName) {
        String l = modelName.toLowerCase();
        // Models that run fully locally via Ollama
        if (l.contains("llama") || l.contains("gemma") || l.contains("phi")
                || l.contains("qwen") || l.contains("deepseek")
                || l.contains("ministral") || l.contains("mistral")
                || l.contains("mixtral")) {
            return "Local";
        }
        return "SaaS";
    }

    // ── JSON pre-processing ────────────────────────────────────────────────────

    /**
     * Strips JavaScript-style single-line comments ({@code // ...}) from a JSON
     * string before parsing.
     *
     * <p>
     * Some local models (notably Qwen 2.5 via OllamaProvider and Mistral) insert
     * {@code //} comments inside their JSON output when reasoning about what
     * selector to use. Standard {@link com.fasterxml.jackson.databind.ObjectMapper}
     * rejects these as invalid JSON, causing an infinite parse-fail loop.
     * This pre-processing step removes trailing comments on each line so that
     * the rest of the pipeline can parse the response normally.
     * </p>
     *
     * <p>
     * The removal is line-by-line and only strips text after {@code //} that
     * appears <em>outside</em> a string literal (simple heuristic: the {@code //}
     * must not be preceded by an odd number of unescaped double-quotes on the
     * same line).
     * </p>
     *
     * @param raw the raw string returned by the LLM
     * @return the string with JS comments removed, or the original if null/blank
     */
    private String stripJsComments(String raw) {
        if (raw == null || raw.isBlank()) return raw;
        // Process line by line: remove everything after // that is outside a string
        StringBuilder sb = new StringBuilder();
        for (String line : raw.split("\n", -1)) {
            sb.append(stripLineComment(line)).append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * Removes a trailing {@code // ...} comment from a single JSON line.
     * Uses a simple quote-counting heuristic to avoid stripping {@code //}
     * that appears inside a string value.
     */
    private String stripLineComment(String line) {
        boolean inString = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (!inString && c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                // Found // outside a string — trim from here and remove trailing comma if any
                String before = line.substring(0, i).stripTrailing();
                if (before.endsWith(",")) {
                    // Keep the comma — it belongs to the JSON structure; just drop the comment
                    return before;
                }
                return before;
            }
        }
        return line;
    }

    // ── Logging helpers ────────────────────────────────────────────────────────

    private void addInfoLog(String msg) {
        LOGGER.log(Level.INFO, msg);
    }

    private void addSevereLog(String msg) {
        LOGGER.log(Level.SEVERE, msg);
    }
}
