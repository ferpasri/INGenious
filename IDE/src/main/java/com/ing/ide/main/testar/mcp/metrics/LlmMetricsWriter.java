package com.ing.ide.main.testar.mcp.metrics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ing.ide.main.testar.trace.AgentTraceViewerWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private static final TypeReference<List<Map<String, Object>>> RUN_LIST_TYPE = new TypeReference<List<Map<String, Object>>>() {};
    private static final TypeReference<Map<String, Object>> RUN_MAP_TYPE = new TypeReference<Map<String, Object>>() {};
    private final LlmMetricsDashboardWriter dashboardWriter = new LlmMetricsDashboardWriter();
    private final AgentTraceViewerWriter traceViewerWriter = new AgentTraceViewerWriter();

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

            Path traceDirectory = metricsRoot.resolve("traces").resolve(metrics.getTraceRunId());
            traceViewerWriter.writeMcpTrace(traceDirectory, payload);

            Path aggregateFile = metricsRoot.resolve("mcp_all_runs.json");
            List<Map<String, Object>> allRuns = loadAllRuns(aggregateFile);
            allRuns.forEach(this::makePathsRelative);
            allRuns.add(payload);
            JSON_MAPPER
                .writerWithDefaultPrettyPrinter()
                .writeValue(aggregateFile.toFile(), allRuns);
            dashboardWriter.writeDashboard(metricsRoot, allRuns);
        } catch (IOException exception) {
            LOGGER.log(Level.SEVERE, "Failed to write LLM MCP metrics", exception);
        }
    }

    public void refreshDashboard(Path metricsRoot) throws IOException {
        Path aggregateFile = metricsRoot.resolve("mcp_all_runs.json");
        List<Map<String, Object>> allRuns = loadAllRuns(aggregateFile);
        Map<String, Map<String, Object>> runsByTraceId = new LinkedHashMap<>();
        for (Map<String, Object> run : allRuns) {
            makePathsRelative(run);
            registerTraceMetadata(runsByTraceId, run);
        }
        if (Files.isRegularFile(aggregateFile)) {
            JSON_MAPPER
                .writerWithDefaultPrettyPrinter()
                .writeValue(aggregateFile.toFile(), allRuns);
        }

        Path runsDirectory = metricsRoot.resolve("runs");
        if (Files.isDirectory(runsDirectory)) {
            try (var runFiles = Files.list(runsDirectory)) {
                for (Path runFile : runFiles
                    .filter(path -> path.toString().endsWith(".json"))
                    .toList()) {
                    Map<String, Object> run = JSON_MAPPER.readValue(runFile.toFile(), RUN_MAP_TYPE);
                    if (makePathsRelative(run)) {
                        JSON_MAPPER
                            .writerWithDefaultPrettyPrinter()
                            .writeValue(runFile.toFile(), run);
                    }
                    registerTraceMetadata(runsByTraceId, run);
                }
            }
        }

        Path tracesDirectory = metricsRoot.resolve("traces");
        if (Files.isDirectory(tracesDirectory)) {
            try (var traceDirectories = Files.list(tracesDirectory)) {
                for (Path traceDirectory : traceDirectories.filter(Files::isDirectory).toList()) {
                    String traceRunId = traceDirectory.getFileName().toString();
                    traceViewerWriter.writeMcpTrace(
                        traceDirectory,
                        runsByTraceId.getOrDefault(traceRunId, Map.of())
                    );
                }
            }
        }
        dashboardWriter.writeDashboard(metricsRoot, allRuns);
    }

    private void registerTraceMetadata(
        Map<String, Map<String, Object>> runsByTraceId,
        Map<String, Object> run
    ) {
        String traceRunId = Objects.toString(run.get("traceRunId"), "");
        if (!traceRunId.isBlank()) {
            runsByTraceId.put(traceRunId, run);
        }
    }

    private boolean makePathsRelative(Map<String, Object> run) {
        boolean changed = !Objects.equals(run.get("projectLocation"), ".");
        run.put("projectLocation", ".");
        String traceRunId = run.get("traceRunId") instanceof String
            ? (String) run.get("traceRunId")
            : "";
        if (!traceRunId.isBlank()) {
            String tracePath = "traces/" + traceRunId + "/trace.html";
            changed |= !Objects.equals(run.get("tracePath"), tracePath);
            run.put("tracePath", tracePath);
        }
        return changed;
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
        payload.put("projectLocation", ".");
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
        payload.put("traceRunId", metrics.getTraceRunId());
        payload.put("tracePath", metrics.getTracePath());
        payload.put("failureMessage", metrics.getFailureMessage());
        return payload;
    }
}
