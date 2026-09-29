package com.ing.ide.main.testar.codex.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ing.ide.main.testar.codex.CodexRunManifest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;

public class CodexMetricsDashboardWriterTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    public void listsInterruptedRunAndTraceWithoutIncludingItInBenchmarkMetrics()
        throws IOException {
        Path codexRoot = Files.createTempDirectory("codex-dashboard-recovery");
        Path runDirectory = codexRoot.resolve("runs").resolve("partial-login-run");
        Files.createDirectories(runDirectory);

        CodexRunManifest manifest = new CodexRunManifest();
        manifest.setRunId("codex-partial-1");
        manifest.setRunName("partial-login-run");
        manifest.setStatus("running");
        manifest.setStartedAt("2026-09-28T10:00:00Z");
        manifest.setTraceRunId("codex-partial-1");
        manifest.setModel("gpt-test");
        manifest.setReasoningEffort("low");
        manifest.setProcessId(Long.MAX_VALUE);
        manifest.setProcessStartedAt(Instant.EPOCH.toString());
        CodexRunManifest.write(runDirectory, manifest);
        JSON_MAPPER.writeValue(
            runDirectory.resolve("request.json").toFile(),
            Map.of(
                "model",
                "gpt-test",
                "reasoningEffort",
                "low",
                "sandboxMode",
                "workspace-write",
                "approvalPolicy",
                "never",
                "networkAccessEnabled",
                true
            )
        );

        Files.writeString(
            runDirectory.resolve("events.jsonl"),
            JSON_MAPPER.writeValueAsString(
                Map.of(
                    "type",
                    "thread.started",
                    "id",
                    "thread-event-1",
                    "runId",
                    "codex-partial-1",
                    "ingeniousReceivedAt",
                    "2026-09-28T10:00:01Z"
                )
            ) +
            System.lineSeparator() +
            "{malformed-last-event"
        );

        new CodexMetricsDashboardWriter().writeDashboard(codexRoot);

        JsonNode payload = readDashboardPayload(codexRoot.resolve("codex_metrics.js"));
        assertThat(payload.path("runs")).isEmpty();
        assertThat(payload.path("summary").path("totalRuns").asInt()).isZero();
        assertThat(payload.path("incompleteRuns")).hasSize(1);
        assertThat(payload.path("incompleteRuns").get(0).path("status").asText())
            .isEqualTo("interrupted");
        assertThat(payload.path("incompleteRuns").get(0).path("tracePath").asText())
            .isEqualTo("runs/partial-login-run/trace.html");
        assertThat(runDirectory.resolve("trace.html")).isRegularFile();
        assertThat(Files.readString(runDirectory.resolve("trace-data.js")))
            .contains(
                "Codex thread started.",
                "\"runName\" : \"partial-login-run\"",
                "\"modelName\" : \"gpt-test\"",
                "\"networkAccessEnabled\" : true"
            );
    }

    @Test
    public void includesCorruptResultAsIncompleteInsteadOfFailingDashboardGeneration()
        throws IOException {
        Path codexRoot = Files.createTempDirectory("codex-dashboard-corrupt-result");
        Path runDirectory = codexRoot.resolve("runs").resolve("corrupt-result-run");
        Files.createDirectories(runDirectory);
        Files.writeString(runDirectory.resolve("result.json"), "{not-json");

        new CodexMetricsDashboardWriter().writeDashboard(codexRoot);

        JsonNode payload = readDashboardPayload(codexRoot.resolve("codex_metrics.js"));
        assertThat(payload.path("runs")).isEmpty();
        assertThat(payload.path("incompleteRuns")).hasSize(1);
        assertThat(payload.path("incompleteRuns").get(0).path("status").asText())
            .isEqualTo("incomplete");
        assertThat(payload.path("incompleteRuns").get(0).path("recoveryNote").asText())
            .contains("no manifest");
    }

    @Test
    public void updatesLegacyRunPathsFromItsCurrentProjectFolder() throws IOException {
        Path projectDirectory = Files
            .createTempDirectory("codex-moved-project")
            .resolve("Projects/Parabank");
        Path codexRoot = projectDirectory.resolve("Results/Codex");
        Path runDirectory = codexRoot.resolve("runs/loan-run");
        Files.createDirectories(runDirectory);
        String oldRelease = "C:\\old-machine\\release";
        String oldProject = oldRelease + "\\Projects\\Parabank";
        Map<String, Object> request = Map.of(
            "workingDirectory",
            oldRelease,
            "outputDirectory",
            oldProject + "\\Results\\Codex\\runs\\loan-run",
            "additionalDirectories",
            List.of(oldRelease, oldProject),
            "networkAccessEnabled",
            false
        );
        Map<String, Object> result = Map.of(
            "status",
            "completed",
            "startedAt",
            "2026-09-28T10:00:00Z",
            "finishedAt",
            "2026-09-28T10:00:10Z",
            "workingDirectory",
            oldRelease,
            "model",
            "gpt-test",
            "completedItems",
            List.of()
        );
        JSON_MAPPER.writeValue(runDirectory.resolve("request.json").toFile(), request);
        JSON_MAPPER.writeValue(runDirectory.resolve("result.json").toFile(), result);
        CodexRunManifest manifest = new CodexRunManifest();
        manifest.setStatus("completed");
        manifest.setProjectPath(oldProject);
        CodexRunManifest.write(runDirectory, manifest);

        new CodexMetricsDashboardWriter().writeDashboard(codexRoot);

        JsonNode updatedRequest = JSON_MAPPER.readTree(
            runDirectory.resolve("request.json").toFile()
        );
        JsonNode updatedResult = JSON_MAPPER.readTree(runDirectory.resolve("result.json").toFile());
        JsonNode dashboard = readDashboardPayload(codexRoot.resolve("codex_metrics.js"));
        assertThat(updatedRequest.path("workingDirectory").asText()).isEqualTo("@release");
        assertThat(updatedRequest.path("outputDirectory").asText()).isEqualTo("@run");
        assertThat(updatedRequest.path("additionalDirectories").get(1).asText())
            .isEqualTo("@project");
        assertThat(updatedResult.path("workingDirectory").asText()).isEqualTo("@release");
        assertThat(CodexRunManifest.read(runDirectory).getProjectPath()).isEqualTo("@project");
        assertThat(dashboard.path("runs").get(0).path("projectName").asText())
            .isEqualTo("Parabank");
    }

    private JsonNode readDashboardPayload(Path dashboardFile) throws IOException {
        String dashboard = Files.readString(dashboardFile);
        int payloadStart = dashboard.indexOf('=') + 1;
        int payloadEnd = dashboard.lastIndexOf(';');
        return JSON_MAPPER.readTree(dashboard.substring(payloadStart, payloadEnd).trim());
    }
}
