package com.ing.ide.main.testar.mcp.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;

public class LlmMetricsWriterTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    public void newRunStoresOnlyProjectRelativeMetadata() throws IOException {
        Path projectDirectory = Files.createTempDirectory("mcp-portable-run");
        LlmRunMetrics metrics = new LlmRunMetrics(
            "login-run",
            "Parabank",
            projectDirectory.toString(),
            null,
            "OpenAI",
            "gpt-test"
        );
        metrics.setTraceReference("mcp-run-2", "traces/mcp-run-2/trace.html");

        new LlmMetricsWriter().writeRunMetrics(metrics);

        Path metricsRoot = projectDirectory.resolve("Results/MCP");
        JsonNode aggregate = JSON_MAPPER.readTree(
            metricsRoot.resolve("mcp_all_runs.json").toFile()
        );
        JsonNode run = aggregate.get(0);
        assertThat(run.path("projectLocation").asText()).isEqualTo(".");
        assertThat(run.path("tracePath").asText()).isEqualTo("traces/mcp-run-2/trace.html");
        assertThat(Files.readString(metricsRoot.resolve("runs/login-run.json")))
            .doesNotContain(projectDirectory.toString());
        assertThat(Files.readString(metricsRoot.resolve("traces/mcp-run-2/trace-data.js")))
            .contains("\"runName\" : \"login-run\"", "\"modelName\" : \"gpt-test\"");
    }

    @Test
    public void refreshesLegacyRunPathsAndTraceViewerAfterProjectMove() throws IOException {
        Path projectDirectory = Files.createTempDirectory("mcp-moved-project");
        Path metricsRoot = projectDirectory.resolve("Results/MCP");
        Path runsDirectory = metricsRoot.resolve("runs");
        Path traceDirectory = metricsRoot.resolve("traces/mcp-run-1");
        Files.createDirectories(runsDirectory);
        Files.createDirectories(traceDirectory);
        Map<String, Object> oldRun = Map.of(
            "runName",
            "loan-run",
            "projectLocation",
            "C:\\old-machine\\Projects\\Parabank",
            "traceRunId",
            "mcp-run-1",
            "tracePath",
            "traces\\mcp-run-1\\trace.html",
            "modelName",
            "gpt-test",
            "startedAt",
            "2026-09-28T10:00:00Z"
        );
        JSON_MAPPER.writeValue(runsDirectory.resolve("loan-run.json").toFile(), oldRun);
        JSON_MAPPER.writeValue(metricsRoot.resolve("mcp_all_runs.json").toFile(), List.of(oldRun));
        Files.writeString(
            traceDirectory.resolve("trace.jsonl"),
            "{\"type\":\"RUN_STARTED\",\"timestamp\":\"2026-09-28T10:00:00Z\"}\n"
        );

        new LlmMetricsWriter().refreshDashboard(metricsRoot);

        JsonNode run = JSON_MAPPER.readTree(runsDirectory.resolve("loan-run.json").toFile());
        JsonNode aggregate = JSON_MAPPER.readTree(
            metricsRoot.resolve("mcp_all_runs.json").toFile()
        );
        assertThat(run.path("projectLocation").asText()).isEqualTo(".");
        assertThat(run.path("tracePath").asText()).isEqualTo("traces/mcp-run-1/trace.html");
        assertThat(aggregate.get(0).path("projectLocation").asText()).isEqualTo(".");
        assertThat(traceDirectory.resolve("trace.html")).isRegularFile();
        assertThat(Files.readString(traceDirectory.resolve("trace-data.js")))
            .contains("\"runName\" : \"loan-run\"", "\"modelName\" : \"gpt-test\"");
        assertThat(metricsRoot.resolve("dashboard.html")).isRegularFile();
    }
}
