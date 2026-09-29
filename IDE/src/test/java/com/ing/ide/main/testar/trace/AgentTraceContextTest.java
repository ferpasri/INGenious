package com.ing.ide.main.testar.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.testng.annotations.Test;

public class AgentTraceContextTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Test
    public void writesOrderedJsonlEventsWithCorrelationIds() throws IOException {
        Path traceFile = Files.createTempDirectory("agent-trace-test").resolve("trace.jsonl");
        AgentTraceContext context = new AgentTraceContext(
            "run-1",
            new JsonlAgentTraceRecorder(traceFile)
        );

        context.beginOperation("operation-1");
        context.append(
            AgentTraceSource.MCP,
            AgentTraceEventType.TOOL_REQUESTED,
            AgentTraceStatus.STARTED,
            "When submit",
            "executeClickAction",
            "call-1",
            Map.of("selector", "#submit"),
            "",
            Map.of(),
            0,
            Map.of(),
            Map.of()
        );
        context.append(
            AgentTraceSource.TESTAR,
            AgentTraceEventType.OPERATION_COMPLETED,
            AgentTraceStatus.SUCCEEDED,
            "When submit",
            "click",
            "call-1",
            Map.of(),
            "Click completed",
            Map.of(),
            12,
            Map.of(),
            Map.of()
        );
        context.endOperation();
        context.close();

        List<String> lines = Files.readAllLines(traceFile);
        assertThat(lines).hasSize(2);
        AgentTraceEvent first = JSON_MAPPER.readValue(lines.get(0), AgentTraceEvent.class);
        AgentTraceEvent second = JSON_MAPPER.readValue(lines.get(1), AgentTraceEvent.class);
        assertThat(first.getSchemaVersion()).isEqualTo(AgentTraceEvent.CURRENT_SCHEMA_VERSION);
        assertThat(first.getRunId()).isEqualTo("run-1");
        assertThat(first.getSequence()).isEqualTo(1);
        assertThat(second.getSequence()).isEqualTo(2);
        assertThat(first.getOperationId()).isEqualTo("operation-1");
        assertThat(second.getOperationId()).isEqualTo("operation-1");
        assertThat(first.getEventId()).isNotEqualTo(second.getEventId());
    }

    @Test
    public void leavesReadablePartialTraceWhenRunDoesNotFinish() throws IOException {
        Path traceFile = Files.createTempDirectory("agent-trace-partial").resolve("trace.jsonl");
        AgentTraceContext context = new AgentTraceContext(
            "partial-run",
            new JsonlAgentTraceRecorder(traceFile)
        );
        context.append(
            AgentTraceSource.CLI,
            AgentTraceEventType.COMMAND_REQUESTED,
            AgentTraceStatus.STARTED,
            "action click",
            "TESTAR CLI request"
        );
        context.close();

        AgentTraceEvent event = JSON_MAPPER.readValue(
            Files.readString(traceFile).trim(),
            AgentTraceEvent.class
        );
        assertThat(event.getType()).isEqualTo(AgentTraceEventType.COMMAND_REQUESTED);
        assertThat(event.getRunId()).isEqualTo("partial-run");
    }

    @Test
    public void redactsSensitiveFieldsRecursivelyAndBearerValues() {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("selector", "#submit");
        arguments.put("password", "private");
        arguments.put("nested", Map.of("api_key", "private-key"));
        arguments.put("message", "Authorization: Bearer abc.def-123");

        Map<String, Object> redacted = TraceValueRedactor.redactMap(arguments);

        assertThat(redacted.get("selector")).isEqualTo("#submit");
        assertThat(redacted.get("password")).isEqualTo("[REDACTED]");
        Map<?, ?> nested = (Map<?, ?>) redacted.get("nested");
        assertThat(nested.get("api_key")).isEqualTo("[REDACTED]");
        assertThat(redacted.get("message").toString()).doesNotContain("abc.def-123");
    }

    @Test
    public void closedRecorderRejectsWritesWithoutCorruptingExistingTrace() throws IOException {
        Path traceFile = Files.createTempDirectory("agent-trace-close").resolve("trace.jsonl");
        JsonlAgentTraceRecorder recorder = new JsonlAgentTraceRecorder(traceFile);
        recorder.close();

        assertThatThrownBy(() -> recorder.append(new AgentTraceEvent()))
            .isInstanceOf(IOException.class);
        assertThat(Files.readString(traceFile)).isEmpty();
    }

    @Test
    public void concurrentAppendsProduceUniqueOrderedSequences() throws Exception {
        Path traceFile = Files.createTempDirectory("agent-trace-concurrent").resolve("trace.jsonl");
        AgentTraceContext context = new AgentTraceContext(
            "concurrent-run",
            new JsonlAgentTraceRecorder(traceFile)
        );
        int eventCount = 40;
        Thread[] writers = new Thread[eventCount];

        for (int index = 0; index < eventCount; index++) {
            int eventIndex = index;
            writers[index] =
                new Thread(
                    () ->
                        context.append(
                            AgentTraceSource.TESTAR,
                            AgentTraceEventType.OPERATION_COMPLETED,
                            AgentTraceStatus.INFO,
                            "event-" + eventIndex
                        )
                );
            writers[index].start();
        }
        for (Thread writer : writers) {
            writer.join();
        }
        context.close();

        List<String> lines = Files.readAllLines(traceFile);
        assertThat(lines).hasSize(eventCount);
        List<AgentTraceEvent> events = lines.stream().map(line -> readEvent(line)).toList();
        assertThat(events)
            .extracting(AgentTraceEvent::getSequence)
            .containsExactlyElementsOf(
                java.util.stream.LongStream.rangeClosed(1, eventCount).boxed().toList()
            );
    }

    private AgentTraceEvent readEvent(String json) {
        try {
            return JSON_MAPPER.readValue(json, AgentTraceEvent.class);
        } catch (IOException exception) {
            throw new AssertionError("Trace event was not valid JSONL", exception);
        }
    }
}
