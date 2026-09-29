package com.ing.ide.main.testar.trace;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AgentTraceContext {
    private static final Logger LOGGER = Logger.getLogger(AgentTraceContext.class.getName());

    private final String runId;
    private final AgentTraceRecorder recorder;
    private final AtomicLong sequence = new AtomicLong();
    private final ThreadLocal<String> currentOperationId = new ThreadLocal<>();
    private final ThreadLocal<String> currentParentEventId = new ThreadLocal<>();

    public AgentTraceContext(String runId, AgentTraceRecorder recorder) {
        this.runId = runId != null && !runId.isBlank() ? runId : UUID.randomUUID().toString();
        this.recorder = recorder != null ? recorder : new NoOpAgentTraceRecorder();
    }

    public String getRunId() {
        return runId;
    }

    public String getCurrentOperationId() {
        return currentOperationId.get();
    }

    public void beginOperation(String operationId) {
        currentOperationId.set(
            operationId != null && !operationId.isBlank()
                ? operationId
                : UUID.randomUUID().toString()
        );
    }

    public void endOperation() {
        currentOperationId.remove();
    }

    public synchronized void append(
        AgentTraceSource source,
        AgentTraceEventType type,
        AgentTraceStatus status,
        String bddStep,
        String operation,
        String callId,
        Map<String, Object> arguments,
        String message,
        Map<String, Object> feedback,
        long durationMs,
        Map<String, Object> evidence,
        Map<String, Object> artifacts
    ) {
        AgentTraceEvent event = new AgentTraceEvent();
        event.setRunId(runId);
        event.setEventId(UUID.randomUUID().toString());
        event.setParentEventId(currentParentEventId.get());
        event.setOperationId(currentOperationId.get());
        event.setSequence(sequence.incrementAndGet());
        event.setTimestamp(OffsetDateTime.now().toString());
        event.setSource(source);
        event.setType(type);
        event.setStatus(status);
        event.setBddStep(bddStep);
        event.setOperation(operation);
        event.setCallId(callId);
        event.setArguments(TraceValueRedactor.redactMap(arguments));
        event.setMessage(TraceValueRedactor.redactString(message));
        event.setFeedback(TraceValueRedactor.redactMap(feedback));
        event.setDurationMs(durationMs);
        event.setEvidence(TraceValueRedactor.redactMap(evidence));
        event.setArtifacts(TraceValueRedactor.redactMap(artifacts));
        try {
            recorder.append(event);
        } catch (IOException | RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Failed to append TESTAR trace event", exception);
        }
    }

    public void append(
        AgentTraceSource source,
        AgentTraceEventType type,
        AgentTraceStatus status,
        String operation,
        String message
    ) {
        append(
            source,
            type,
            status,
            "",
            operation,
            "",
            new LinkedHashMap<>(),
            message,
            new LinkedHashMap<>(),
            0L,
            new LinkedHashMap<>(),
            new LinkedHashMap<>()
        );
    }

    public void append(
        AgentTraceSource source,
        AgentTraceEventType type,
        AgentTraceStatus status,
        String message
    ) {
        append(source, type, status, "", message);
    }

    public void close() {
        try {
            recorder.close();
        } catch (IOException | RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Failed to close TESTAR trace recorder", exception);
        }
    }
}
