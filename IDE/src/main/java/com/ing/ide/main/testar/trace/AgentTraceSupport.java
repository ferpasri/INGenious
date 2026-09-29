package com.ing.ide.main.testar.trace;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ing.ide.main.testar.mcp.Feedback;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AgentTraceSupport {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {};

    private AgentTraceSupport() {}

    public static void appendFeedback(
        AgentTraceContext traceContext,
        AgentTraceSource source,
        String bddStep,
        String operation,
        Feedback feedback,
        long durationMs
    ) {
        if (traceContext == null) {
            return;
        }

        Map<String, Object> structuredFeedback = new LinkedHashMap<>();
        if (feedback != null) {
            structuredFeedback.put("code", feedback.getCode().name());
            structuredFeedback.put("issue", feedback.isIssue());
            structuredFeedback.put("message", feedback.toString());
        }
        traceContext.append(
            source,
            AgentTraceEventType.FEEDBACK_PROVIDED,
            feedback != null && feedback.isIssue()
                ? AgentTraceStatus.FAILED
                : AgentTraceStatus.SUCCEEDED,
            bddStep,
            operation,
            "",
            Map.of(),
            feedback != null
                ? safeObservation(TraceValueRedactor.redactString(feedback.toString()), 2000)
                : "No feedback returned.",
            structuredFeedback,
            durationMs,
            Map.of(),
            Map.of()
        );
    }

    public static void appendArtifacts(
        AgentTraceContext traceContext,
        String bddStep,
        String operation,
        ArtifactPersistenceResult persistenceResult
    ) {
        if (traceContext == null || persistenceResult == null) {
            return;
        }

        for (ArtifactReference artifact : persistenceResult.getArtifacts()) {
            Map<String, Object> artifactData = JSON_MAPPER.convertValue(artifact, MAP_TYPE);
            AgentTraceEventType eventType = "reused".equals(artifact.getDisposition())
                ? AgentTraceEventType.ARTIFACT_REUSED
                : AgentTraceEventType.ARTIFACT_CREATED;
            traceContext.append(
                AgentTraceSource.PERSISTENCE,
                eventType,
                AgentTraceStatus.SUCCEEDED,
                bddStep,
                operation,
                "",
                Map.of(),
                artifact.getKind() + " " + artifact.getDisposition(),
                Map.of(),
                0L,
                Map.of(),
                artifactData
            );
        }
    }

    public static Map<String, Object> arguments(Object... keyValues) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            arguments.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return arguments;
    }

    public static String safeObservation(String observation, int maxCharacters) {
        if (observation == null) {
            return "";
        }
        String normalized = observation.trim();
        if (normalized.length() <= maxCharacters) {
            return normalized;
        }
        return normalized.substring(0, maxCharacters) + "...[truncated]";
    }
}
