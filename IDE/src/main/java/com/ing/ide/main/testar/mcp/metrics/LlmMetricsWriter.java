package com.ing.ide.main.testar.mcp.metrics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persist TESTAR Studio MCP run metrics in a report-friendly format.
 *
 * <p>
 * The writer stores both a per-run JSON file and an aggregated
 * {@code mcp_all_runs.json} file so later dashboards can visualize historical
 * runs without needing to parse the execution log.
 * </p>
 */
public class LlmMetricsWriter {

    private static final Logger LOGGER = Logger.getLogger(LlmMetricsWriter.class.getName());
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Map<String, Object>>> RUN_LIST_TYPE =
            new TypeReference<List<Map<String, Object>>>() { };
    private final LlmMetricsDashboardWriter dashboardWriter = new LlmMetricsDashboardWriter();

    public void writeRunMetrics(LlmRunMetrics metrics) {
        if (metrics == null || metrics.getProjectLocation().isBlank()) {
            return;
        }

        try {
            Path metricsRoot = Paths.get(metrics.getProjectLocation(), "Results", "MCP");
            Path runsDir = metricsRoot.resolve("runs");
            Files.createDirectories(runsDir);

            Map<String, Object> payload = toPayload(metrics);
            Path runFile = runsDir.resolve(metrics.getRunFileSafeName() + ".json");
            JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(runFile.toFile(), payload);

            Path aggregateFile = metricsRoot.resolve("mcp_all_runs.json");
            List<Map<String, Object>> allRuns = loadAllRuns(aggregateFile);
            allRuns.add(payload);
            JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(aggregateFile.toFile(), allRuns);
            dashboardWriter.writeDashboard(metricsRoot, allRuns);
        } catch (IOException exception) {
            LOGGER.log(Level.SEVERE, "Failed to write LLM MCP metrics", exception);
        }
    }

    private List<Map<String, Object>> loadAllRuns(Path aggregateFile) throws IOException {
        if (!Files.exists(aggregateFile)) {
            return new ArrayList<>();
        }

        return JSON_MAPPER.readValue(aggregateFile.toFile(), RUN_LIST_TYPE);
    }

    private Map<String, Object> toPayload(LlmRunMetrics metrics) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runName", metrics.getRunName());
        payload.put("runTimestamp", metrics.getRunTimestamp());
        payload.put("projectName", metrics.getProjectName());
        payload.put("projectLocation", metrics.getProjectLocation());
        payload.put("scenarioName", metrics.getScenarioName());
        payload.put("providerName", metrics.getProviderName());
        payload.put("modelName", metrics.getModelName());
        payload.put("reasoningLevel", metrics.getReasoningLevel());
        payload.put("visionEnabled", metrics.isVisionEnabled());
        payload.put("apiUrl", metrics.getApiUrl());
        payload.put("startedAt", metrics.getStartedAt());
        payload.put("finishedAt", metrics.getFinishedAt());
        payload.put("completedSuccess", metrics.isCompletedSuccess());
        payload.put("totalSteps", metrics.getTotalSteps());
        payload.put("invalidActions", metrics.getInvalidActions());
        payload.put("totalTokens", metrics.getTotalTokens());
        payload.put("retries", metrics.getRetries());
        payload.put("noToolResponses", metrics.getNoToolResponses());
        payload.put("toolCalls", metrics.getToolCalls());
        payload.put("visionRequests", metrics.getVisionRequests());
        payload.put("avgLatencyMs", metrics.getAverageLatencyMs());
        payload.put("latencyTimelineMs", metrics.getLatencyTimelineMs());
        payload.put("invalidActionReasonCounts", metrics.getInvalidActionReasonCounts());
        payload.put("invalidActionsByTool", metrics.getInvalidActionsByTool());
        payload.put("invalidActionDetails", metrics.getInvalidActionDetails());
        payload.put("knownBddSteps", metrics.getKnownBddSteps());
        payload.put("executedBddSteps", metrics.getExecutedBddSteps());
        payload.put("latestExecutedBddStep", metrics.getLatestExecutedBddStep());
        payload.put("failureMessage", metrics.getFailureMessage());
        return payload;
    }
}
