package com.ing.ide.main.testar.codex.metrics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ing.engine.constants.AppResourcePath;
import com.ing.ide.main.testar.codex.CodexRunManifest;
import com.ing.ide.main.testar.trace.AgentTraceViewerWriter;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public class CodexMetricsDashboardWriter {
    private static final Logger LOGGER = Logger.getLogger(
        CodexMetricsDashboardWriter.class.getName()
    );
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {};
    private static final String DASHBOARD_TEMPLATE_HTML = "codex-dashboard.html";
    private static final String DASHBOARD_CSS = "codex-dashboard.css";
    private static final String DASHBOARD_JS = "codex-dashboard.js";
    private final AgentTraceViewerWriter traceViewerWriter = new AgentTraceViewerWriter();

    public void writeDashboard(Path codexRoot) throws IOException {
        Files.createDirectories(codexRoot);

        List<Map<String, Object>> runs = new ArrayList<>();
        List<Map<String, Object>> incompleteRuns = new ArrayList<>();
        loadRuns(codexRoot.resolve("runs"), runs, incompleteRuns);
        Map<String, Object> dashboardData = buildDashboardData(runs, incompleteRuns);
        String jsPayload =
            "window.CODEX_METRICS_DATA = " +
            JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(dashboardData) +
            ";";

        Files.writeString(codexRoot.resolve("codex_metrics.js"), jsPayload, StandardCharsets.UTF_8);
        copyDashboardAssets(codexRoot);
    }

    private void loadRuns(
        Path runsDir,
        List<Map<String, Object>> runs,
        List<Map<String, Object>> incompleteRuns
    )
        throws IOException {
        if (!Files.isDirectory(runsDir)) {
            return;
        }

        try (Stream<Path> paths = Files.list(runsDir)) {
            paths
                .filter(Files::isDirectory)
                .sorted()
                .forEach(
                    runDir -> {
                        Map<String, Object> result;
                        try {
                            result = readJsonMap(runDir.resolve("result.json"));
                        } catch (IOException exception) {
                            result = new LinkedHashMap<>();
                        }
                        Map<String, Object> request;
                        try {
                            request = readJsonMap(runDir.resolve("request.json"));
                        } catch (IOException exception) {
                            request = new LinkedHashMap<>();
                        }
                        try {
                            makeRunMetadataPortable(runDir, result, request);
                        } catch (IOException exception) {
                            LOGGER.log(
                                Level.WARNING,
                                "Could not update Codex run paths",
                                exception
                            );
                        }
                        if (result.isEmpty()) {
                            if (hasRunEvidence(runDir)) {
                                incompleteRuns.add(loadIncompleteRun(runDir, request));
                            }
                            return;
                        }
                        Map<String, Object> normalized = normalizeRun(runDir, result, request);
                        if (
                            Files.isRegularFile(runDir.resolve("events.jsonl")) ||
                            Files.isRegularFile(runDir.resolve("testar-trace.jsonl"))
                        ) {
                            attachTraceLink(runDir, normalized);
                        }
                        runs.add(normalized);
                    }
                );
        }

        runs.sort(Comparator.comparing(run -> String.valueOf(run.get("startedAt"))));
        incompleteRuns.sort(Comparator.comparing(run -> String.valueOf(run.get("startedAt"))));
    }

    private void makeRunMetadataPortable(
        Path runDirectory,
        Map<String, Object> result,
        Map<String, Object> request
    )
        throws IOException {
        if (!request.isEmpty() && !"@release".equals(request.get("workingDirectory"))) {
            String oldRelease = stringValue(request.get("workingDirectory"));
            String oldProject = "";
            Object directoriesValue = request.get("additionalDirectories");
            if (directoriesValue instanceof List<?>) {
                List<?> directories = (List<?>) directoriesValue;
                if (directories.size() > 1) {
                    oldProject = stringValue(directories.get(1));
                }
            }
            request.put("workingDirectory", "@release");
            request.put("outputDirectory", "@run");
            if (directoriesValue instanceof List<?>) {
                List<?> directories = (List<?>) directoriesValue;
                List<String> portableDirectories = new ArrayList<>();
                for (Object directory : directories) {
                    String value = stringValue(directory);
                    if (value.equalsIgnoreCase(oldRelease)) {
                        portableDirectories.add("@release");
                    } else if (!oldProject.isBlank() && value.equalsIgnoreCase(oldProject)) {
                        portableDirectories.add("@project");
                    } else {
                        portableDirectories.add(value);
                    }
                }
                request.put("additionalDirectories", portableDirectories);
            }
            JSON_MAPPER
                .writerWithDefaultPrettyPrinter()
                .writeValue(runDirectory.resolve("request.json").toFile(), request);
        }
        if (!result.isEmpty() && !"@release".equals(result.get("workingDirectory"))) {
            result.put("workingDirectory", "@release");
            JSON_MAPPER
                .writerWithDefaultPrettyPrinter()
                .writeValue(runDirectory.resolve("result.json").toFile(), result);
        }

        Path manifestPath = runDirectory.resolve(CodexRunManifest.FILE_NAME);
        if (Files.isRegularFile(manifestPath)) {
            CodexRunManifest manifest = CodexRunManifest.read(runDirectory);
            if (
                !"starting".equalsIgnoreCase(manifest.getObservedStatus()) &&
                !"running".equalsIgnoreCase(manifest.getObservedStatus()) &&
                !"@project".equals(manifest.getProjectPath())
            ) {
                manifest.setProjectPath("@project");
                CodexRunManifest.write(runDirectory, manifest);
            }
        }
    }

    private boolean hasRunEvidence(Path runDirectory) {
        return (
            Files.isRegularFile(runDirectory.resolve(CodexRunManifest.FILE_NAME)) ||
            Files.isRegularFile(runDirectory.resolve("request.json")) ||
            Files.isRegularFile(runDirectory.resolve("result.json")) ||
            Files.isRegularFile(runDirectory.resolve("events.jsonl")) ||
            Files.isRegularFile(runDirectory.resolve("testar-trace.jsonl")) ||
            Files.isRegularFile(runDirectory.resolve("stdout.log")) ||
            Files.isRegularFile(runDirectory.resolve("stderr.log"))
        );
    }

    private Map<String, Object> loadIncompleteRun(Path runDirectory, Map<String, Object> request) {
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("runName", runDirectory.getFileName().toString());
        run.put("status", "incomplete");
        run.put("startedAt", "");
        run.put("modelName", stringValue(request.get("model")));
        run.put("reasoningEffort", stringValue(request.get("reasoningEffort")));
        run.put("sandboxMode", stringValue(request.get("sandboxMode")));
        run.put("approvalPolicy", stringValue(request.get("approvalPolicy")));
        run.put("networkAccessEnabled", booleanValue(request.get("networkAccessEnabled")));
        run.put("projectName", projectName(runDirectory));
        run.put("tracePath", "");

        Path manifestPath = runDirectory.resolve(CodexRunManifest.FILE_NAME);
        if (Files.isRegularFile(manifestPath)) {
            try {
                CodexRunManifest manifest = CodexRunManifest.read(runDirectory);
                String observedStatus = manifest.getObservedStatus();
                String recordedStatus = manifest.getStatus();
                run.put(
                    "status",
                    "completed".equalsIgnoreCase(observedStatus) ||
                        "failed".equalsIgnoreCase(observedStatus) ||
                        "cancelled".equalsIgnoreCase(observedStatus)
                        ? "incomplete"
                        : observedStatus
                );
                if (
                    "interrupted".equals(observedStatus) &&
                    (
                        "running".equalsIgnoreCase(recordedStatus) ||
                        "starting".equalsIgnoreCase(recordedStatus)
                    )
                ) {
                    run.put("recoveryNote", "The recorded process is no longer running.");
                } else if ("incomplete".equals(run.get("status"))) {
                    run.put("recoveryNote", "No complete run result was recorded.");
                } else if (recordedStatus.equalsIgnoreCase(observedStatus)) {
                    run.put("recoveryNote", "Recorded run status: " + recordedStatus + ".");
                }
                run.put("recordedStatus", recordedStatus);
                run.put("runId", manifest.getRunId());
                run.put("startedAt", manifest.getStartedAt());
                if (!manifest.getModel().isBlank()) {
                    run.put("modelName", manifest.getModel());
                }
                if (!manifest.getReasoningEffort().isBlank()) {
                    run.put("reasoningEffort", manifest.getReasoningEffort());
                }
            } catch (IOException exception) {
                run.put("recoveryNote", "Run manifest could not be read.");
            }
        } else {
            run.put("recoveryNote", "Legacy run has no manifest; process status is unknown.");
        }

        attachTraceLink(runDirectory, run);
        return run;
    }

    private void attachTraceLink(Path runDirectory, Map<String, Object> run) {
        try {
            traceViewerWriter.writeCodexTrace(runDirectory, run);
            run.put("tracePath", "runs/" + runDirectory.getFileName() + "/trace.html");
        } catch (IOException exception) {
            run.put("traceRenderError", "Trace viewer could not be generated.");
        }
    }

    private Map<String, Object> normalizeRun(
        Path runDir,
        Map<String, Object> result,
        Map<String, Object> request
    ) {
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
        run.put("projectName", projectName(runDir));
        run.put("workingDirectory", "@release");
        run.put("threadId", stringValue(result.get("threadId")));
        run.put("modelName", stringValue(result.get("model")));
        run.put("reasoningEffort", stringValue(result.get("reasoningEffort")));
        run.put("sandboxMode", stringValue(result.get("sandboxMode")));
        run.put("approvalPolicy", stringValue(result.get("approvalPolicy")));
        run.put("networkAccessEnabled", booleanValue(request.get("networkAccessEnabled")));
        run.put("prompt", stringValue(result.get("prompt")));
        run.put("status", stringValue(result.get("status")));
        run.put(
            "completedSuccess",
            "completed".equalsIgnoreCase(stringValue(result.get("status")))
        );
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

    private String projectName(Path runDirectory) {
        Path projectDirectory = runDirectory.getParent().getParent().getParent().getParent();
        Path fileName = projectDirectory.getFileName();
        return fileName != null ? fileName.toString() : "";
    }

    private Map<String, Object> buildDashboardData(
        List<Map<String, Object>> runs,
        List<Map<String, Object>> incompleteRuns
    ) {
        Map<String, GroupAggregate> byGroup = new LinkedHashMap<>();

        for (Map<String, Object> run : runs) {
            String groupKey =
                stringValue(run.get("modelName")) +
                "||" +
                stringValue(run.get("reasoningEffort")) +
                "||" +
                stringValue(run.get("sandboxMode")) +
                "||" +
                stringValue(run.get("approvalPolicy")) +
                "||" +
                booleanValue(run.get("networkAccessEnabled"));

            byGroup
                .computeIfAbsent(
                    groupKey,
                    key ->
                        new GroupAggregate(
                            stringValue(run.get("modelName")),
                            stringValue(run.get("reasoningEffort")),
                            stringValue(run.get("sandboxMode")),
                            stringValue(run.get("approvalPolicy")),
                            booleanValue(run.get("networkAccessEnabled"))
                        )
                )
                .accept(run);
        }

        List<Map<String, Object>> groups = new ArrayList<>();
        for (GroupAggregate aggregate : byGroup.values()) {
            groups.add(aggregate.toMap());
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRuns", runs.size());
        summary.put(
            "successfulRuns",
            runs.stream().filter(run -> booleanValue(run.get("completedSuccess"))).count()
        );
        summary.put(
            "failedRuns",
            runs.stream().filter(run -> !booleanValue(run.get("completedSuccess"))).count()
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("groups", groups);
        payload.put("runs", runs);
        payload.put("incompleteRuns", incompleteRuns);
        return payload;
    }

    private void copyDashboardAssets(Path metricsRoot) throws IOException {
        Path htmlSource = resolveReportAsset(
            Paths.get(AppResourcePath.getReportTemplatePath(), DASHBOARD_TEMPLATE_HTML),
            Paths.get("html", DASHBOARD_TEMPLATE_HTML)
        );
        Path cssSource = resolveReportAsset(
            Paths.get(AppResourcePath.getReportResourcePath(), "css", DASHBOARD_CSS),
            Paths.get("media", "css", DASHBOARD_CSS)
        );
        Path jsSource = resolveReportAsset(
            Paths.get(AppResourcePath.getReportResourcePath(), "js", DASHBOARD_JS),
            Paths.get("media", "js", DASHBOARD_JS)
        );

        Path cssTargetDir = metricsRoot.resolve("media").resolve("css");
        Path jsTargetDir = metricsRoot.resolve("media").resolve("js");

        Files.createDirectories(cssTargetDir);
        Files.createDirectories(jsTargetDir);

        Files.copy(
            htmlSource,
            metricsRoot.resolve("dashboard.html"),
            StandardCopyOption.REPLACE_EXISTING
        );
        Files.copy(
            cssSource,
            cssTargetDir.resolve(DASHBOARD_CSS),
            StandardCopyOption.REPLACE_EXISTING
        );
        Files.copy(
            jsSource,
            jsTargetDir.resolve(DASHBOARD_JS),
            StandardCopyOption.REPLACE_EXISTING
        );
    }

    private Map<String, Object> readJsonMap(Path file) throws IOException {
        if (!Files.exists(file)) {
            return new LinkedHashMap<>();
        }
        return JSON_MAPPER.readValue(file.toFile(), MAP_TYPE);
    }

    private Path resolveReportAsset(Path configuredPath, Path sourceRelativePath)
        throws IOException {
        if (Files.isRegularFile(configuredPath)) {
            return configuredPath;
        }

        Path sourceRoot = Paths.get("Resources", "Configuration", "ReportTemplate");
        for (Path candidate : List.of(
            sourceRoot.resolve(sourceRelativePath),
            Paths
                .get("..", "Resources", "Configuration", "ReportTemplate")
                .resolve(sourceRelativePath)
                .normalize()
        )) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        throw new IOException("Codex dashboard asset was not found: " + configuredPath);
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
            return Duration
                .between(OffsetDateTime.parse(startedAt), OffsetDateTime.parse(finishedAt))
                .toMillis();
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

        private GroupAggregate(
            String modelName,
            String reasoningEffort,
            String sandboxMode,
            String approvalPolicy,
            boolean networkAccessEnabled
        ) {
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
                itemTypeCounts.merge(
                    entry.getKey(),
                    (int) longValue(entry.getValue()),
                    Integer::sum
                );
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
            map.put(
                "avgCompletedItems",
                totalRuns == 0 ? 0.0d : (double) totalCompletedItems / totalRuns
            );
            map.put(
                "avgFailedCommandCount",
                totalRuns == 0 ? 0.0d : (double) totalFailedCommandCount / totalRuns
            );
            map.put("itemTypeCounts", itemTypeCounts);
            return map;
        }
    }
}
