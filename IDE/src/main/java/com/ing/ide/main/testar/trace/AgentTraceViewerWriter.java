package com.ing.ide.main.testar.trace;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ing.engine.constants.AppResourcePath;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AgentTraceViewerWriter {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {};
    private static final String TRACE_HTML = "agent-trace.html";
    private static final String TRACE_CSS = "agent-trace.css";
    private static final String TRACE_JS = "agent-trace.js";

    public Path writeMcpTrace(Path traceDirectory) throws IOException {
        return writeMcpTrace(traceDirectory, Map.of());
    }

    public Path writeMcpTrace(Path traceDirectory, Map<String, Object> runMetadata)
        throws IOException {
        List<Map<String, Object>> events = readJsonLines(traceDirectory.resolve("trace.jsonl"));
        String runId = traceDirectory.getFileName() != null
            ? traceDirectory.getFileName().toString()
            : "";
        String fallbackName = events
            .stream()
            .filter(event -> "RUN_STARTED".equals(stringValue(event.get("type"))))
            .map(event -> mapValue(event.get("arguments")))
            .map(arguments -> stringValue(arguments.get("scenarioName")))
            .filter(value -> !value.isBlank())
            .findFirst()
            .orElse(runId);
        String runName = stringValue(runMetadata.get("runName"));
        writeViewer(
            traceDirectory,
            events,
            "Studio MCP trace",
            runId,
            runName.isBlank() ? fallbackName : runName,
            "mcp",
            mcpModelGroup(runMetadata)
        );
        return traceDirectory.resolve("trace.html");
    }

    public Path writeCodexTrace(Path runDirectory) throws IOException {
        return writeCodexTrace(runDirectory, Map.of());
    }

    public Path writeCodexTrace(Path runDirectory, Map<String, Object> runMetadata)
        throws IOException {
        List<Map<String, Object>> events = new ArrayList<>();
        events.addAll(readCodexEvents(runDirectory.resolve("events.jsonl")));
        events.addAll(readJsonLines(runDirectory.resolve("testar-trace.jsonl")));
        events.sort(
            Comparator.comparing(this::eventInstant, Comparator.nullsLast(Instant::compareTo))
        );
        for (int index = 0; index < events.size(); index++) {
            events.get(index).put("viewerSequence", index + 1);
        }
        String runId = events
            .stream()
            .map(event -> stringValue(event.get("runId")))
            .filter(value -> !value.isBlank())
            .findFirst()
            .orElse("");
        String runName = runDirectory.getFileName() != null
            ? runDirectory.getFileName().toString()
            : runId;
        writeViewer(
            runDirectory,
            events,
            "Codex and TESTAR trace",
            runId,
            runName,
            "codex",
            codexModelGroup(runMetadata)
        );
        return runDirectory.resolve("trace.html");
    }

    private Map<String, Object> mcpModelGroup(Map<String, Object> runMetadata) {
        if (stringValue(runMetadata.get("modelName")).isBlank()) {
            return Map.of();
        }
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("providerName", stringValue(runMetadata.get("providerName")));
        group.put("modelName", stringValue(runMetadata.get("modelName")));
        group.put("reasoningLevel", stringValue(runMetadata.get("reasoningLevel")));
        group.put("visionEnabled", Boolean.TRUE.equals(runMetadata.get("visionEnabled")));
        return group;
    }

    private Map<String, Object> codexModelGroup(Map<String, Object> runMetadata) {
        if (stringValue(runMetadata.get("modelName")).isBlank()) {
            return Map.of();
        }
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("modelName", stringValue(runMetadata.get("modelName")));
        group.put("reasoningEffort", stringValue(runMetadata.get("reasoningEffort")));
        group.put("sandboxMode", stringValue(runMetadata.get("sandboxMode")));
        group.put("approvalPolicy", stringValue(runMetadata.get("approvalPolicy")));
        group.put(
            "networkAccessEnabled",
            Boolean.TRUE.equals(runMetadata.get("networkAccessEnabled"))
        );
        return group;
    }

    private List<Map<String, Object>> readJsonLines(Path path) throws IOException {
        List<Map<String, Object>> events = new ArrayList<>();
        if (!Files.isRegularFile(path)) {
            return events;
        }
        int sequence = 0;
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            try {
                Map<String, Object> event = JSON_MAPPER.readValue(line, MAP_TYPE);
                event.putIfAbsent("viewerSequence", ++sequence);
                events.add(event);
            } catch (IOException exception) {
                Map<String, Object> invalid = new LinkedHashMap<>();
                invalid.put("type", "MALFORMED_EVENT");
                invalid.put("source", "TRACE");
                invalid.put("status", "FAILED");
                invalid.put("message", "A trace line could not be parsed.");
                invalid.put("raw", TraceValueRedactor.redactMap(Map.of("line", line)));
                invalid.put("viewerSequence", ++sequence);
                events.add(invalid);
            }
        }
        return events;
    }

    private List<Map<String, Object>> readCodexEvents(Path path) throws IOException {
        List<Map<String, Object>> events = new ArrayList<>();
        if (!Files.isRegularFile(path)) {
            return events;
        }

        int sequence = 0;
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode root;
            try {
                root = JSON_MAPPER.readTree(line);
            } catch (IOException exception) {
                continue;
            }
            Map<String, Object> normalized = normalizeCodexEvent(root);
            if (normalized == null) {
                continue;
            }
            normalized.put("source", "CODEX");
            normalized.put("runId", root.path("runId").asText(""));
            normalized.put("timestamp", root.path("ingeniousReceivedAt").asText(""));
            normalized.put("viewerSequence", ++sequence);
            events.add(TraceValueRedactor.redactMap(normalized));
        }
        return events;
    }

    private Map<String, Object> normalizeCodexEvent(JsonNode event) {
        String type = event.path("type").asText("");
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("eventId", event.path("id").asText(""));
        normalized.put("callId", event.path("item").path("id").asText(""));

        switch (type) {
            case "thread.started":
                normalized.put("type", "RUN_STARTED");
                normalized.put("status", "INFO");
                normalized.put("message", "Codex thread started.");
                return normalized;
            case "turn.started":
                normalized.put("type", "TURN_STARTED");
                normalized.put("status", "INFO");
                normalized.put("message", "Codex turn started.");
                return normalized;
            case "turn.completed":
                normalized.put("type", "TURN_COMPLETED");
                normalized.put("status", "SUCCEEDED");
                normalized.put("message", "Codex turn completed.");
                normalized.put("evidence", JSON_MAPPER.convertValue(event.path("usage"), MAP_TYPE));
                return normalized;
            case "turn.failed":
                normalized.put("type", "RUN_ERROR");
                normalized.put("status", "FAILED");
                normalized.put(
                    "message",
                    event.path("error").path("message").asText("Turn failed.")
                );
                return normalized;
            case "error":
                normalized.put("type", "RUN_ERROR");
                normalized.put("status", "FAILED");
                normalized.put("message", event.path("message").asText("Codex stream error."));
                return normalized;
            case "item.started":
            case "item.completed":
                return normalizeCodexItem(event, normalized, "item.completed".equals(type));
            default:
                return null;
        }
    }

    private Map<String, Object> normalizeCodexItem(
        JsonNode event,
        Map<String, Object> normalized,
        boolean completed
    ) {
        JsonNode item = event.path("item");
        String itemType = item.path("type").asText("");
        normalized.put("status", completed ? "SUCCEEDED" : "STARTED");
        normalized.put("callId", item.path("id").asText(""));
        switch (itemType) {
            case "agent_message":
                normalized.put("type", "AGENT_MESSAGE");
                normalized.put("message", item.path("text").asText(""));
                return normalized;
            case "reasoning":
                normalized.put("type", "DECISION_SUMMARY");
                normalized.put("message", item.path("text").asText(""));
                return normalized;
            case "command_execution":
                normalized.put("type", completed ? "COMMAND_COMPLETED" : "COMMAND_REQUESTED");
                normalized.put("operation", item.path("command").asText(""));
                normalized.put("message", item.path("aggregated_output").asText(""));
                normalized.put("exitCode", item.path("exit_code").asInt(-1));
                normalized.put("commandStatus", item.path("status").asText(""));
                if (completed) {
                    boolean failed =
                        "failed".equalsIgnoreCase(item.path("status").asText("")) ||
                        (item.path("exit_code").isNumber() && item.path("exit_code").asInt() != 0);
                    normalized.put("status", failed ? "FAILED" : "SUCCEEDED");
                }
                return normalized;
            case "mcp_tool_call":
                normalized.put("type", "TOOL_REQUESTED");
                normalized.put("operation", item.path("tool").asText(""));
                normalized.put("message", item.path("error").path("message").asText(""));
                return normalized;
            case "file_change":
                normalized.put("type", "FILE_CHANGE");
                normalized.put("message", "Codex reported file changes.");
                return normalized;
            case "error":
                normalized.put("type", "RUN_ERROR");
                normalized.put("status", "FAILED");
                normalized.put("message", item.path("message").asText(""));
                return normalized;
            default:
                return null;
        }
    }

    private void writeViewer(
        Path outputDirectory,
        List<Map<String, Object>> events,
        String title,
        String runId,
        String runName,
        String agentType,
        Map<String, Object> modelGroup
    )
        throws IOException {
        Files.createDirectories(outputDirectory);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", AgentTraceEvent.CURRENT_SCHEMA_VERSION);
        payload.put("title", title);
        payload.put("runId", runId);
        payload.put("runName", runName);
        payload.put("agentType", agentType);
        payload.put("modelGroup", modelGroup);
        payload.put("events", events);
        Files.writeString(
            outputDirectory.resolve("trace-data.js"),
            "window.AGENT_TRACE_DATA = " +
            JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload) +
            ";",
            StandardCharsets.UTF_8
        );

        Path htmlSource = resolveReportAsset(
            Paths.get(AppResourcePath.getReportTemplatePath(), TRACE_HTML),
            Paths.get("html", TRACE_HTML)
        );
        Path cssSource = resolveReportAsset(
            Paths.get(AppResourcePath.getReportResourcePath(), "css", TRACE_CSS),
            Paths.get("media", "css", TRACE_CSS)
        );
        Path jsSource = resolveReportAsset(
            Paths.get(AppResourcePath.getReportResourcePath(), "js", TRACE_JS),
            Paths.get("media", "js", TRACE_JS)
        );
        Path cssTarget = outputDirectory.resolve("media").resolve("css");
        Path jsTarget = outputDirectory.resolve("media").resolve("js");
        Files.createDirectories(cssTarget);
        Files.createDirectories(jsTarget);
        Files.copy(
            htmlSource,
            outputDirectory.resolve("trace.html"),
            StandardCopyOption.REPLACE_EXISTING
        );
        Files.copy(cssSource, cssTarget.resolve(TRACE_CSS), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(jsSource, jsTarget.resolve(TRACE_JS), StandardCopyOption.REPLACE_EXISTING);
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

        throw new IOException("Trace viewer asset was not found: " + configuredPath);
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    private Instant eventInstant(Map<String, Object> event) {
        String timestamp = stringValue(event.get("timestamp"));
        if (timestamp.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(timestamp);
        } catch (RuntimeException ignored) {
            try {
                return OffsetDateTime.parse(timestamp).toInstant();
            } catch (RuntimeException invalidTimestamp) {
                return null;
            }
        }
    }
}
