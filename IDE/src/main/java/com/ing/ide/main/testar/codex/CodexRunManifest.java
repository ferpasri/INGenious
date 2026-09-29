package com.ing.ide.main.testar.codex;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;

/** Durable metadata for discovering Codex runs when result.json was not finalized. */
public class CodexRunManifest {
    public static final String FILE_NAME = "run-manifest.json";

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private int schemaVersion = 1;
    private String runId = "";
    private String runName = "";
    private String status = "starting";
    private String startedAt = "";
    private String finishedAt = "";
    private String projectPath = "";
    private String traceRunId = "";
    private String model = "";
    private String reasoningEffort = "";
    private long processId = -1L;
    private String processStartedAt = "";

    public static Path write(Path runDirectory, CodexRunManifest manifest) throws IOException {
        Files.createDirectories(runDirectory);
        Path manifestPath = runDirectory.resolve(FILE_NAME);
        Path temporaryPath = runDirectory.resolve(FILE_NAME + ".tmp");
        JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(temporaryPath.toFile(), manifest);
        try {
            Files.move(
                temporaryPath,
                manifestPath,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryPath, manifestPath, StandardCopyOption.REPLACE_EXISTING);
        }
        return manifestPath;
    }

    public static CodexRunManifest read(Path runDirectory) throws IOException {
        return JSON_MAPPER.readValue(
            runDirectory.resolve(FILE_NAME).toFile(),
            CodexRunManifest.class
        );
    }

    /** Resolve a stale running state only after checking the exact child process identity. */
    @JsonIgnore
    public String getObservedStatus() {
        boolean starting = "starting".equalsIgnoreCase(status);
        if (!starting && !"running".equalsIgnoreCase(status)) {
            return status;
        }
        if (processId <= 0) {
            return "interrupted";
        }

        Optional<ProcessHandle> process = ProcessHandle.of(processId);
        if (process.isEmpty() || !process.get().isAlive()) {
            return "interrupted";
        }

        String actualStart = process.get().info().startInstant().map(Instant::toString).orElse("");
        if (!processStartedAt.isBlank() && !actualStart.isBlank()) {
            try {
                if (!Instant.parse(processStartedAt).equals(Instant.parse(actualStart))) {
                    return "interrupted";
                }
            } catch (RuntimeException exception) {
                return "interrupted";
            }
        }
        return starting ? "starting" : "running";
    }

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

    public String getRunName() {
        return runName;
    }

    public void setRunName(String runName) {
        this.runName = valueOrEmpty(runName);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = valueOrEmpty(status);
    }

    public String getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(String startedAt) {
        this.startedAt = valueOrEmpty(startedAt);
    }

    public String getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(String finishedAt) {
        this.finishedAt = valueOrEmpty(finishedAt);
    }

    public String getProjectPath() {
        return projectPath;
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = valueOrEmpty(projectPath);
    }

    public String getTraceRunId() {
        return traceRunId;
    }

    public void setTraceRunId(String traceRunId) {
        this.traceRunId = valueOrEmpty(traceRunId);
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = valueOrEmpty(model);
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = valueOrEmpty(reasoningEffort);
    }

    public long getProcessId() {
        return processId;
    }

    public void setProcessId(long processId) {
        this.processId = processId;
    }

    public String getProcessStartedAt() {
        return processStartedAt;
    }

    public void setProcessStartedAt(String processStartedAt) {
        this.processStartedAt = valueOrEmpty(processStartedAt);
    }

    private static String valueOrEmpty(String value) {
        return value != null ? value : "";
    }
}
