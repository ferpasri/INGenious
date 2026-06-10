package com.ing.ide.main.testar.mcp.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ing.engine.constants.AppResourcePath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generate a standalone MCP metrics dashboard for benchmark-style run review.
 */
public class LlmMetricsDashboardWriter {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private static final String DASHBOARD_TEMPLATE_HTML = "mcp-dashboard.html";
    private static final String DASHBOARD_CSS = "mcp-dashboard.css";
    private static final String DASHBOARD_JS = "mcp-dashboard.js";

    public void writeDashboard(Path metricsRoot, List<Map<String, Object>> allRuns) throws IOException {
        Files.createDirectories(metricsRoot);

        Map<String, Object> dashboardData = buildDashboardData(allRuns);
        String jsPayload = "window.MCP_METRICS_DATA = " + JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(dashboardData) + ";";

        Files.writeString(metricsRoot.resolve("mcp_metrics.js"), jsPayload, StandardCharsets.UTF_8);
        copyDashboardAssets(metricsRoot);
    }

    private void copyDashboardAssets(Path metricsRoot) throws IOException {
        Path htmlSource = Paths.get(AppResourcePath.getReportTemplatePath(), DASHBOARD_TEMPLATE_HTML);
        Path cssSource = Paths.get(AppResourcePath.getReportResourcePath(), "css", DASHBOARD_CSS);
        Path jsSource = Paths.get(AppResourcePath.getReportResourcePath(), "js", DASHBOARD_JS);

        Path cssTargetDir = metricsRoot.resolve("media").resolve("css");
        Path jsTargetDir = metricsRoot.resolve("media").resolve("js");

        Files.createDirectories(cssTargetDir);
        Files.createDirectories(jsTargetDir);

        Files.copy(htmlSource, metricsRoot.resolve("dashboard.html"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(cssSource, cssTargetDir.resolve(DASHBOARD_CSS), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(jsSource, jsTargetDir.resolve(DASHBOARD_JS), StandardCopyOption.REPLACE_EXISTING);
    }

    private Map<String, Object> buildDashboardData(List<Map<String, Object>> allRuns) {
        List<Map<String, Object>> runs = new ArrayList<>(allRuns);
        runs.sort(Comparator.comparing(run -> String.valueOf(run.get("startedAt"))));

        Map<String, ModelAggregate> byModel = new LinkedHashMap<>();
        Map<String, Integer> invalidReasonCounts = new LinkedHashMap<>();
        Map<String, Integer> invalidToolCounts = new LinkedHashMap<>();

        for (Map<String, Object> run : runs) {
            String modelKey = String.valueOf(run.get("providerName"))
                    + "||" + String.valueOf(run.get("modelName"))
                    + "||" + String.valueOf(run.get("reasoningLevel"))
                    + "||" + String.valueOf(run.get("visionEnabled"));
            byModel.computeIfAbsent(modelKey, key -> new ModelAggregate(
                    String.valueOf(run.get("providerName")),
                    String.valueOf(run.get("modelName")),
                    String.valueOf(run.get("reasoningLevel")),
                    Boolean.TRUE.equals(run.get("visionEnabled"))
            )).accept(run);

            mergeCounts(invalidReasonCounts, mapValue(run.get("invalidActionReasonCounts")));
            mergeCounts(invalidToolCounts, mapValue(run.get("invalidActionsByTool")));
        }

        List<Map<String, Object>> models = new ArrayList<>();
        for (ModelAggregate aggregate : byModel.values()) {
            models.add(aggregate.toMap());
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRuns", runs.size());
        summary.put("successfulRuns", countSuccess(runs));
        summary.put("failedRuns", runs.size() - countSuccess(runs));
        summary.put("invalidReasonCounts", invalidReasonCounts);
        summary.put("invalidActionsByTool", invalidToolCounts);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("models", models);
        payload.put("runs", runs);
        return payload;
    }

    private Map<String, Integer> mapValue(Object value) {
        if (!(value instanceof Map)) {
            return new LinkedHashMap<>();
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        ((Map<?, ?>) value).forEach((key, count) -> result.put(String.valueOf(key), intValue(count)));
        return result;
    }

    private void mergeCounts(Map<String, Integer> target, Map<String, Integer> source) {
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }

    private int countSuccess(List<Map<String, Object>> runs) {
        int success = 0;
        for (Map<String, Object> run : runs) {
            if (Boolean.TRUE.equals(run.get("completedSuccess"))) {
                success++;
            }
        }
        return success;
    }

    private int intValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception exception) {
            return 0;
        }
    }

    private long longValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception exception) {
            return 0L;
        }
    }

    private double rate(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0.0d;
        }
        return (100.0d * numerator) / denominator;
    }

    private final class ModelAggregate {

        private final String providerName;
        private final String modelName;
        private final String reasoningLevel;
        private final boolean visionEnabled;
        private final Map<String, Integer> invalidReasonCounts = new LinkedHashMap<>();
        private final Map<String, Integer> invalidActionsByTool = new LinkedHashMap<>();
        private int totalRuns;
        private int successfulRuns;
        private long totalLatencyMs;
        private long totalTokens;
        private long totalInvalidActions;
        private long totalSteps;

        private ModelAggregate(String providerName, String modelName, String reasoningLevel, boolean visionEnabled) {
            this.providerName = providerName;
            this.modelName = modelName;
            this.reasoningLevel = reasoningLevel;
            this.visionEnabled = visionEnabled;
        }

        private void accept(Map<String, Object> run) {
            totalRuns++;
            if (Boolean.TRUE.equals(run.get("completedSuccess"))) {
                successfulRuns++;
            }
            totalLatencyMs += longValue(run.get("avgLatencyMs"));
            totalTokens += longValue(run.get("totalTokens"));
            totalInvalidActions += longValue(run.get("invalidActions"));
            totalSteps += longValue(run.get("totalSteps"));
            mergeCounts(invalidReasonCounts, mapValue(run.get("invalidActionReasonCounts")));
            mergeCounts(invalidActionsByTool, mapValue(run.get("invalidActionsByTool")));
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("providerName", providerName);
            map.put("modelName", modelName);
            map.put("reasoningLevel", reasoningLevel);
            map.put("visionEnabled", visionEnabled);
            map.put("totalRuns", totalRuns);
            map.put("successfulRuns", successfulRuns);
            map.put("successRate", rate(successfulRuns, totalRuns));
            map.put("avgLatencyMs", totalRuns == 0 ? 0 : totalLatencyMs / totalRuns);
            map.put("avgTokens", totalRuns == 0 ? 0 : totalTokens / totalRuns);
            map.put("avgInvalidActions", totalRuns == 0 ? 0.0d : (double) totalInvalidActions / totalRuns);
            map.put("avgSteps", totalRuns == 0 ? 0.0d : (double) totalSteps / totalRuns);
            map.put("invalidReasonCounts", invalidReasonCounts);
            map.put("invalidActionsByTool", invalidActionsByTool);
            return map;
        }
    }
}
