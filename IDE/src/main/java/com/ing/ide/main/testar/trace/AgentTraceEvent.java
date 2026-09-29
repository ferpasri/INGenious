package com.ing.ide.main.testar.trace;

import java.util.LinkedHashMap;
import java.util.Map;

public class AgentTraceEvent {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private String runId = "";
    private String eventId = "";
    private String parentEventId = "";
    private String operationId = "";
    private long sequence;
    private String timestamp = "";
    private AgentTraceSource source = AgentTraceSource.TESTAR;
    private AgentTraceEventType type = AgentTraceEventType.OPERATION_COMPLETED;
    private AgentTraceStatus status = AgentTraceStatus.INFO;
    private String bddStep = "";
    private String operation = "";
    private String callId = "";
    private Map<String, Object> arguments = new LinkedHashMap<>();
    private String message = "";
    private Map<String, Object> feedback = new LinkedHashMap<>();
    private long durationMs;
    private Map<String, Object> evidence = new LinkedHashMap<>();
    private Map<String, Object> artifacts = new LinkedHashMap<>();

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(int schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = valueOrEmpty(runId);
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = valueOrEmpty(eventId);
    }

    public String getParentEventId() {
        return parentEventId;
    }

    public void setParentEventId(String parentEventId) {
        this.parentEventId = valueOrEmpty(parentEventId);
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = valueOrEmpty(operationId);
    }

    public long getSequence() {
        return sequence;
    }

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = valueOrEmpty(timestamp);
    }

    public AgentTraceSource getSource() {
        return source;
    }

    public void setSource(AgentTraceSource source) {
        this.source = source != null ? source : AgentTraceSource.TESTAR;
    }

    public AgentTraceEventType getType() {
        return type;
    }

    public void setType(AgentTraceEventType type) {
        this.type = type != null ? type : AgentTraceEventType.OPERATION_COMPLETED;
    }

    public AgentTraceStatus getStatus() {
        return status;
    }

    public void setStatus(AgentTraceStatus status) {
        this.status = status != null ? status : AgentTraceStatus.INFO;
    }

    public String getBddStep() {
        return bddStep;
    }

    public void setBddStep(String bddStep) {
        this.bddStep = valueOrEmpty(bddStep);
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = valueOrEmpty(operation);
    }

    public String getCallId() {
        return callId;
    }

    public void setCallId(String callId) {
        this.callId = valueOrEmpty(callId);
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Map<String, Object> arguments) {
        this.arguments = arguments != null ? arguments : new LinkedHashMap<>();
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = valueOrEmpty(message);
    }

    public Map<String, Object> getFeedback() {
        return feedback;
    }

    public void setFeedback(Map<String, Object> feedback) {
        this.feedback = feedback != null ? feedback : new LinkedHashMap<>();
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = Math.max(0L, durationMs);
    }

    public Map<String, Object> getEvidence() {
        return evidence;
    }

    public void setEvidence(Map<String, Object> evidence) {
        this.evidence = evidence != null ? evidence : new LinkedHashMap<>();
    }

    public Map<String, Object> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(Map<String, Object> artifacts) {
        this.artifacts = artifacts != null ? artifacts : new LinkedHashMap<>();
    }

    private String valueOrEmpty(String value) {
        return value != null ? value : "";
    }
}
