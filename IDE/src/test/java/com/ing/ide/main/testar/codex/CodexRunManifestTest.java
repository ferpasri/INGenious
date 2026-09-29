package com.ing.ide.main.testar.codex;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.testng.annotations.Test;

public class CodexRunManifestTest {

    @Test
    public void writesAndReadsRunIdentityAndStatus() throws IOException {
        Path runDirectory = Files.createTempDirectory("codex-run-manifest");
        CodexRunManifest manifest = new CodexRunManifest();
        manifest.setRunId("codex-run-1");
        manifest.setRunName("login-run");
        manifest.setStatus("running");
        manifest.setStartedAt("2026-09-28T10:00:00Z");
        manifest.setProjectPath("Projects/Parabank");
        manifest.setTraceRunId("codex-run-1");
        manifest.setModel("gpt-test");
        manifest.setReasoningEffort("low");
        manifest.setProcessId(Long.MAX_VALUE);
        manifest.setProcessStartedAt("2026-09-28T10:00:01Z");

        CodexRunManifest.write(runDirectory, manifest);
        CodexRunManifest restored = CodexRunManifest.read(runDirectory);

        assertThat(restored.getRunId()).isEqualTo("codex-run-1");
        assertThat(restored.getRunName()).isEqualTo("login-run");
        assertThat(restored.getProjectPath()).isEqualTo("Projects/Parabank");
        assertThat(restored.getModel()).isEqualTo("gpt-test");
        assertThat(restored.getObservedStatus()).isEqualTo("interrupted");
    }

    @Test
    public void reportsCurrentProcessAsRunningWhenStartIdentityMatches() {
        CodexRunManifest manifest = new CodexRunManifest();
        manifest.setStatus("running");
        manifest.setProcessId(ProcessHandle.current().pid());
        manifest.setProcessStartedAt(
            ProcessHandle.current().info().startInstant().map(Instant::toString).orElse("")
        );

        assertThat(manifest.getObservedStatus()).isEqualTo("running");
    }

    @Test
    public void keepsStartingStatusWhileItsOwnerProcessIsAlive() {
        CodexRunManifest manifest = new CodexRunManifest();
        manifest.setStatus("starting");
        manifest.setProcessId(ProcessHandle.current().pid());
        manifest.setProcessStartedAt(
            ProcessHandle.current().info().startInstant().map(Instant::toString).orElse("")
        );

        assertThat(manifest.getObservedStatus()).isEqualTo("starting");
    }

    @Test
    public void preservesTerminalStatusWithoutProcessLookup() {
        CodexRunManifest manifest = new CodexRunManifest();
        manifest.setStatus("cancelled");
        manifest.setProcessId(Long.MAX_VALUE);

        assertThat(manifest.getObservedStatus()).isEqualTo("cancelled");
    }
}
