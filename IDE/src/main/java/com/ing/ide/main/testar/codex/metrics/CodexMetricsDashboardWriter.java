package com.ing.ide.main.testar.codex.metrics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ing.engine.constants.AppResourcePath;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class CodexMetricsDashboardWriter {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() { };
    private static final String DASHBOARD_TEMPLATE_HTML = "codex-dashboard.html";
    private static final String DASHBOARD_CSS = "codex-dashboard.css";
    private static final String DASHBOARD_JS = "codex-dashboard.js";

    public void writeDashboard(Path codexRoot) throws IOException {
        Files.createDirectories(codexRoot);

        List<Map<String, Object>> runs = loadRuns(codexRoot.resolve("runs"));
        Map<String, Object> dashboardData = buildDashboardData(runs);
        String jsPayload = "window.CODEX_METRICS_DATA = "
                + JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(dashboardData)
                + ";";

        Files.writeString(codexRoot.resolve("codex_metrics.js"), jsPayload, StandardCharsets.UTF_8);
        copyDashboardAssets(codexRoot);
    }

    private List<Map<String, Object>> loadRuns(Path runsDir) throws IOException {
        List<Map<String, Object>> runs = new ArrayList<>();
        if (!Files.isDirectory(runsDir)) {
            return runs;
        }

        try (Stream<Path> paths = Files.list(runsDir)) {
            paths.filter(Files::isDirectory)
                    .sorted()
                    .forEach(runDir -> {
                        try {
                            Map<String, Object> result = readJsonMap(runDir.resolve("result.json"));
                            Map<String, Object> request = readJsonMap(runDir.resolve("request.json"));
                            if (result.isEmpty()) {
                                return;
                            }
                            runs.add(normalizeRun(runDir, result, request));
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    });
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof IOException) {
                throw (IOException) exception.getCause();
            }
            throw exception;
        }

        runs.sort(Comparator.comparing(run -> String.valueOf(run.get("startedAt"))));
        return runs;
    }

    private Map<String, Object> normalizeRun(Path runDir,
                                             Map<String, Object> result,
                                             Map<String, Object> request) {
        Map<String, Integer> itemTypeCounts = new LinkedHashMap<>();
        int commandCount = 0;
        int failedCommandCount = 0;
        int completedCommandCount = 0;
        int mcpToolCallCount = 0;
        int failedMcpToolCalls = 0;

        List<Map<String, Object>> completedItems = listOfMaps(result.get("completedItems"));
        for (Map<String, Object> item : completedItems) {
            String type = stringValue(item.get("type"));
            itemTypeCounts.merge(type, 1, Integer::sum);

            if ("command_execution".equals(type)) {
                commandCount++;
                String status = stringValue(item.get("status"));
                if ("completed".equalsIgnoreCase(status)) {
                    completedCommandCount++;
                } else if ("failed".equalsIgnoreCase(status)) {
                    failedCommandCount++;
                }
            }

            if ("mcp_tool_call".equals(type)) {
                mcpToolCallCount++;
                if ("failed".equalsIgnoreCase(stringValue(item.get("status")))) {
                    failedMcpToolCalls++;
                }
            }
        }

        Map<String, Object> usage = mapValue(result.get("usage"));
        long inputTokens = longValue(usage.get("input_tokens"));
        long cachedInputTokens = longValue(usage.get("cached_input_tokens"));
        long outputTokens = longValue(usage.get("output_tokens"));
        long reasoningOutputTokens = longValue(usage.get("reasoning_output_tokens"));
        long totalTokens = inputTokens + outputTokens + reasoningOutputTokens;

        String startedAt = stringValue(result.get("startedAt"));
        String finishedAt = stringValue(result.get("finishedAt"));

        Map<String, Object> run = new LinkedHashMap<>();
        run.put("runName", runDir.getFileName().toString());
        run.put("projectName", projectName(result, request));
        run.put("workingDirectory", stringValue(result.get("workingDirectory")));
        run.put("threadId", stringValue(result.get("threadId")));
        run.put("modelName", stringValue(result.get("model")));
        run.put("reasoningEffort", stringValue(result.get("reasoningEffort")));
        run.put("sandboxMode", stringValue(result.get("sandboxMode")));
        run.put("approvalPolicy", stringValue(result.get("approvalPolicy")));
        run.put("networkAccessEnabled", booleanValue(request.get("networkAccessEnabled")));
        run.put("prompt", stringValue(result.get("prompt")));
        run.put("status", stringValue(result.get("status")));
        run.put("completedSuccess", "completed".equalsIgnoreCase(stringValue(result.get("status"))));
        run.put("startedAt", startedAt);
        run.put("finishedAt", finishedAt);
        run.put("durationMs", durationMillis(startedAt, finishedAt));
        run.put("inputTokens", inputTokens);
        run.put("cachedInputTokens", cachedInputTokens);
        run.put("outputTokens", outputTokens);
        run.put("reasoningOutputTokens", reasoningOutputTokens);
        run.put("totalTokens", totalTokens);
        run.put("completedItems", completedItems.size());
        run.put("commandCount", commandCount);
        run.put("completedCommandCount", completedCommandCount);
        run.put("failedCommandCount", failedCommandCount);
        run.put("mcpToolCallCount", mcpToolCallCount);
        run.put("failedMcpToolCalls", failedMcpToolCalls);
        run.put("itemTypeCounts", itemTypeCounts);
        run.put("finalResponse", stringValue(result.get("finalResponse")));
        run.put("errorMessage", stringValue(result.get("errorMessage")));
        return run;
    }

    private String projectName(Map<String, Object> result, Map<String, Object> request) {
        String workingDirectory = stringValue(result.get("workingDirectory"));
        if (!workingDirectory.isBlank()) {
            Path path = Paths.get(workingDirectory);
            Path fileName = path.getFileName();
            if (fileName != null) {
                return fileName.toString();
            }
        }

        String projectPath = stringValue(request.get("workingDirectory"));
        if (!projectPath.isBlank()) {
            Path path = Paths.get(projectPath);
            Path fileName = path.getFileName();
            if (fileName != null) {
                return fileName.toString();
            }
        }

        return "";
    }

    private Map<String, Object> buildDashboardData(List<Map<String, Object>> runs) {
        Map<String, GroupAggregate> byGroup = new LinkedHashMap<>();

        for (Map<String, Object> run : runs) {
            String groupKey = stringValue(run.get("modelName"))
                    + "||" + stringValue(run.get("reasoningEffort"))
                    + "||" + stringValue(run.get("sandboxMode"))
                    + "||" + stringValue(run.get("approvalPolicy"))
                    + "||" + booleanValue(run.get("networkAccessEnabled"));

            byGroup.computeIfAbsent(groupKey, key -> new GroupAggregate(
                    stringValue(run.get("modelName")),
                    stringValue(run.get("reasoningEffort")),
                    stringValue(run.get("sandboxMode")),
                    stringValue(run.get("approvalPolicy")),
                    booleanValue(run.get("networkAccessEnabled"))
            )).accept(run);
        }

        List<Map<String, Object>> groups = new ArrayList<>();
        for (GroupAggregate aggregate : byGroup.values()) {
            groups.add(aggregate.toMap());
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRuns", runs.size());
        summary.put("successfulRuns", runs.stream().filter(run -> booleanValue(run.get("completedSuccess"))).count());
        summary.put("failedRuns", runs.stream().filter(run -> !booleanValue(run.get("completedSuccess"))).count());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("groups", groups);
        payload.put("runs", runs);
        return payload;
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

    private Map<String, Object> readJsonMap(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        return JSON_MAPPER.readValue(file.toFile(), MAP_TYPE);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List)) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : (List<?>) value) {
            if (item instanceof Map) {
                result.add(new LinkedHashMap<>((Map<String, Object>) item));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map)) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>((Map<String, Object>) value);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(stringValue(value));
    }

    private long longValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(stringValue(value));
        } catch (Exception exception) {
            return 0L;
        }
    }

    private long durationMillis(String startedAt, String finishedAt) {
        try {
            if (startedAt.isBlank() || finishedAt.isBlank()) {
                return 0L;
            }
            return Duration.between(OffsetDateTime.parse(startedAt), OffsetDateTime.parse(finishedAt)).toMillis();
        } catch (Exception exception) {
            return 0L;
        }
    }

    private final class GroupAggregate {

        private final String modelName;
        private final String reasoningEffort;
        private final String sandboxMode;
        private final String approvalPolicy;
        private final boolean networkAccessEnabled;
        private final Map<String, Integer> itemTypeCounts = new LinkedHashMap<>();
        private int totalRuns;
        private int successfulRuns;
        private long totalDurationMs;
        private long totalTokens;
        private long totalCompletedItems;
        private long totalFailedCommandCount;

        private GroupAggregate(String modelName,
                               String reasoningEffort,
                               String sandboxMode,
                               String approvalPolicy,
                               boolean networkAccessEnabled) {
            this.modelName = modelName;
            this.reasoningEffort = reasoningEffort;
            this.sandboxMode = sandboxMode;
            this.approvalPolicy = approvalPolicy;
            this.networkAccessEnabled = networkAccessEnabled;
        }

        private void accept(Map<String, Object> run) {
            totalRuns++;
            if (booleanValue(run.get("completedSuccess"))) {
                successfulRuns++;
            }
            totalDurationMs += longValue(run.get("durationMs"));
            totalTokens += longValue(run.get("totalTokens"));
            totalCompletedItems += longValue(run.get("completedItems"));
            totalFailedCommandCount += longValue(run.get("failedCommandCount"));

            Map<String, Object> counts = mapValue(run.get("itemTypeCounts"));
            for (Map.Entry<String, Object> entry : counts.entrySet()) {
                itemTypeCounts.merge(entry.getKey(), (int) longValue(entry.getValue()), Integer::sum);
            }
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("modelName", modelName);
            map.put("reasoningEffort", reasoningEffort);
            map.put("sandboxMode", sandboxMode);
            map.put("approvalPolicy", approvalPolicy);
            map.put("networkAccessEnabled", networkAccessEnabled);
            map.put("totalRuns", totalRuns);
            map.put("successfulRuns", successfulRuns);
            map.put("successRate", totalRuns == 0 ? 0.0d : (100.0d * successfulRuns) / totalRuns);
            map.put("avgDurationMs", totalRuns == 0 ? 0L : totalDurationMs / totalRuns);
            map.put("avgTotalTokens", totalRuns == 0 ? 0L : totalTokens / totalRuns);
            map.put("avgCompletedItems", totalRuns == 0 ? 0.0d : (double) totalCompletedItems / totalRuns);
            map.put("avgFailedCommandCount", totalRuns == 0 ? 0.0d : (double) totalFailedCommandCount / totalRuns);
            map.put("itemTypeCounts", itemTypeCounts);
            return map;
        }
    }
}
