package com.ing.ide.main.testar.trace;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.testng.annotations.Test;

public class AgentTraceViewerWriterTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    public void writesMcpTracePageAndSharedAssets() throws IOException {
        Path traceDirectory = Files.createTempDirectory("mcp-trace-viewer");
        Files.writeString(
            traceDirectory.resolve("trace.jsonl"),
            JSON_MAPPER.writeValueAsString(
                Map.of(
                    "type",
                    "TOOL_REQUESTED",
                    "source",
                    "MCP",
                    "status",
                    "STARTED",
                    "bddStep",
                    "When submit",
                    "operation",
                    "executeClickAction"
                )
            ) +
            System.lineSeparator()
        );

        Path viewer = new AgentTraceViewerWriter()
        .writeMcpTrace(
                traceDirectory,
                Map.of(
                    "runName",
                    "Parabank Loan_2026-09-29_12-10-59",
                    "providerName",
                    "OpenAI",
                    "modelName",
                    "gpt-test",
                    "reasoningLevel",
                    "low",
                    "visionEnabled",
                    true
                )
            );

        assertThat(viewer).isRegularFile();
        String traceData = Files.readString(traceDirectory.resolve("trace-data.js"));
        assertThat(traceData)
            .contains(
                "TOOL_REQUESTED",
                "When submit",
                "Parabank Loan_2026-09-29_12-10-59",
                "\"providerName\" : \"OpenAI\"",
                "\"modelName\" : \"gpt-test\"",
                "\"visionEnabled\" : true"
            );
        assertThat(traceDirectory.resolve("media/css/agent-trace.css")).isRegularFile();
        assertThat(traceDirectory.resolve("media/js/agent-trace.js")).isRegularFile();
    }

    @Test
    public void combinesCodexAndTestarEventsInTimestampOrder() throws IOException {
        Path runDirectory = Files.createTempDirectory("codex-trace-viewer");
        Files.writeString(
            runDirectory.resolve("events.jsonl"),
            JSON_MAPPER.writeValueAsString(
                Map.of(
                    "type",
                    "item.completed",
                    "id",
                    "event-codex",
                    "ingeniousReceivedAt",
                    "2026-09-28T10:00:02Z",
                    "item",
                    Map.of("type", "agent_message", "text", "Agent chose submit")
                )
            ) +
            System.lineSeparator()
        );
        Files.writeString(
            runDirectory.resolve("testar-trace.jsonl"),
            JSON_MAPPER.writeValueAsString(
                Map.of(
                    "type",
                    "OPERATION_COMPLETED",
                    "source",
                    "TESTAR",
                    "status",
                    "SUCCEEDED",
                    "timestamp",
                    "2026-09-28T12:00:01+02:00",
                    "operation",
                    "action.click"
                )
            ) +
            System.lineSeparator()
        );

        new AgentTraceViewerWriter()
        .writeCodexTrace(
                runDirectory,
                Map.of(
                    "modelName",
                    "gpt-test",
                    "reasoningEffort",
                    "medium",
                    "sandboxMode",
                    "workspace-write",
                    "approvalPolicy",
                    "never",
                    "networkAccessEnabled",
                    false
                )
            );

        String payload = Files.readString(runDirectory.resolve("trace-data.js"));
        assertThat(payload.indexOf("action.click"))
            .isLessThan(payload.indexOf("Agent chose submit"));
        assertThat(payload).contains("CODEX", "TESTAR", "viewerSequence", "codex-trace-viewer");
        assertThat(payload)
            .contains("\"modelName\" : \"gpt-test\"", "\"networkAccessEnabled\" : false");
    }

    @Test
    public void usesCommandExitStatusForCompletedCodexItems() throws IOException {
        Path runDirectory = Files.createTempDirectory("codex-command-status");
        Files.writeString(
            runDirectory.resolve("events.jsonl"),
            JSON_MAPPER.writeValueAsString(
                Map.of(
                    "type",
                    "item.completed",
                    "item",
                    Map.of(
                        "id",
                        "failed-command",
                        "type",
                        "command_execution",
                        "command",
                        "rg --files missing",
                        "exit_code",
                        1,
                        "status",
                        "failed"
                    )
                )
            ) +
            System.lineSeparator() +
            JSON_MAPPER.writeValueAsString(
                Map.of(
                    "type",
                    "item.completed",
                    "item",
                    Map.of(
                        "id",
                        "successful-command",
                        "type",
                        "command_execution",
                        "command",
                        "rg --files existing",
                        "exit_code",
                        0,
                        "status",
                        "completed"
                    )
                )
            )
        );

        new AgentTraceViewerWriter().writeCodexTrace(runDirectory);

        String payload = Files.readString(runDirectory.resolve("trace-data.js"));
        String json = payload.substring(
            "window.AGENT_TRACE_DATA = ".length(),
            payload.length() - 1
        );
        JsonNode events = JSON_MAPPER.readTree(json).path("events");
        assertThat(events.get(0).path("status").asText()).isEqualTo("FAILED");
        assertThat(events.get(0).path("commandStatus").asText()).isEqualTo("failed");
        assertThat(events.get(1).path("status").asText()).isEqualTo("SUCCEEDED");
    }

    @Test
    public void missingTraceCreatesAnEmptyViewerInsteadOfFailing() throws IOException {
        Path traceDirectory = Files.createTempDirectory("empty-trace-viewer");

        new AgentTraceViewerWriter().writeMcpTrace(traceDirectory);

        assertThat(Files.readString(traceDirectory.resolve("trace-data.js")))
            .contains("\"events\" : [ ]");
    }

    @Test
    public void malformedJsonlLineDoesNotDiscardValidEvents() throws IOException {
        Path traceDirectory = Files.createTempDirectory("malformed-trace-viewer");
        Files.writeString(
            traceDirectory.resolve("trace.jsonl"),
            "{not-json}" +
            System.lineSeparator() +
            JSON_MAPPER.writeValueAsString(Map.of("type", "RUN_STARTED", "source", "MCP"))
        );

        new AgentTraceViewerWriter().writeMcpTrace(traceDirectory);

        String payload = Files.readString(traceDirectory.resolve("trace-data.js"));
        assertThat(payload).contains("MALFORMED_EVENT", "RUN_STARTED");
    }
}
