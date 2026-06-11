package com.ing.ide.main.testar.codex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ing.datalib.component.Project;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class CodexAgentRunner {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter RUN_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault());

    public CodexRunResult run(Project project, CodexAgentSettings settings) throws IOException, InterruptedException {
        RunningCodexRun run = start(project, settings, null);
        return run.awaitCompletion();
    }

    public RunningCodexRun start(Project project,
                                 CodexAgentSettings settings,
                                 LogListener logListener) throws IOException {
        if (project == null || project.getLocation() == null || project.getLocation().isBlank()) {
            throw new IllegalStateException("Please open a project before running the Codex agent.");
        }

        Path releaseRoot = resolveReleaseRoot();
        validateRuntime(releaseRoot);

        Path runDirectory = createRunDirectory(project, settings);
        Path requestFile = runDirectory.resolve("request.json");
        Path stdoutFile = runDirectory.resolve("stdout.log");
        Path stderrFile = runDirectory.resolve("stderr.log");
        Path resultFile = runDirectory.resolve("result.json");

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("apiKeyEnvVarName", valueOrDefault(settings.apiKeyEnvVarName, "OPENAI_API_KEY"));
        request.put("baseUrl", valueOrEmpty(settings.baseUrl));
        request.put("model", valueOrDefault(settings.model, "gpt-5.4"));
        request.put("reasoningEffort", valueOrDefault(settings.reasoningEffort, "medium"));
        request.put("sandboxMode", valueOrDefault(settings.sandboxMode, "workspace-write"));
        request.put("approvalPolicy", valueOrDefault(settings.approvalPolicy, "never"));
        request.put("networkAccessEnabled", settings.networkAccessEnabled != null && settings.networkAccessEnabled);
        request.put("skipGitRepoCheck", settings.skipGitRepoCheck == null || settings.skipGitRepoCheck);
        request.put("workingDirectory", releaseRoot.toAbsolutePath().normalize().toString());
        request.put("additionalDirectories", additionalDirectories(releaseRoot, project.getLocation()));
        request.put("prompt", buildExecutionPrompt(valueOrEmpty(settings.promptText), project.getLocation()));
        request.put("outputDirectory", runDirectory.toAbsolutePath().toString());
        JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValue(requestFile.toFile(), request);

        ProcessBuilder processBuilder = new ProcessBuilder(
                "node",
                releaseRoot.resolve("codex").resolve("ingenious-codex-runner.mjs").toString(),
                requestFile.toString()
        );
        processBuilder.directory(releaseRoot.toFile());

        Process process = processBuilder.start();
        RunningCodexRun run = new RunningCodexRun(process, runDirectory, stdoutFile, stderrFile, resultFile, logListener);
        run.startStreaming();
        return run;
    }

    public interface LogListener {

        void onStdout(String line);

        void onStderr(String line);

    }

    public final class RunningCodexRun {

        private final Process process;
        private final Path runDirectory;
        private final Path stdoutFile;
        private final Path stderrFile;
        private final Path resultFile;
        private final LogListener logListener;
        private final AtomicBoolean stopRequested = new AtomicBoolean(false);
        private final CountDownLatch streamLatch = new CountDownLatch(2);

        private RunningCodexRun(Process process,
                                Path runDirectory,
                                Path stdoutFile,
                                Path stderrFile,
                                Path resultFile,
                                LogListener logListener) {
            this.process = process;
            this.runDirectory = runDirectory;
            this.stdoutFile = stdoutFile;
            this.stderrFile = stderrFile;
            this.resultFile = resultFile;
            this.logListener = logListener;
        }

        public Path getRunDirectory() {
            return runDirectory;
        }

        public boolean isAlive() {
            return process.isAlive();
        }

        public void requestStop() {
            if (!stopRequested.compareAndSet(false, true)) {
                return;
            }

            destroyProcessTree(process.toHandle());
            process.destroy();
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }

        public CodexRunResult awaitCompletion() throws InterruptedException, IOException {
            int exitCode = process.waitFor();
            streamLatch.await();

            if (stopRequested.get() && !Files.exists(resultFile)) {
                return new CodexRunResult(
                        false,
                        "cancelled",
                        "",
                        "",
                        "Codex agent execution cancelled.",
                        runDirectory,
                        null
                );
            }

            if (!Files.exists(resultFile)) {
                String stderr = Files.exists(stderrFile) ? Files.readString(stderrFile) : "";
                throw new IllegalStateException("Codex runner did not produce result.json. " + stderr.trim());
            }

            JsonNode root = JSON_MAPPER.readTree(resultFile.toFile());
            String status = readText(root, "status");
            String threadId = readText(root, "threadId");
            String finalResponse = readText(root, "finalResponse");
            String errorMessage = readText(root, "errorMessage");

            CodexRunResult.Usage usage = null;
            JsonNode usageNode = root.get("usage");
            if (usageNode != null && !usageNode.isNull()) {
                usage = JSON_MAPPER.convertValue(usageNode, CodexRunResult.Usage.class);
            }

            boolean success = exitCode == 0 && "completed".equalsIgnoreCase(status);
            if (!success && errorMessage.isBlank()) {
                String stderr = Files.exists(stderrFile) ? Files.readString(stderrFile) : "";
                errorMessage = stderr.isBlank() ? "Codex agent execution failed." : stderr.trim();
            }

            return new CodexRunResult(
                    success,
                    status,
                    threadId,
                    finalResponse,
                    errorMessage,
                    runDirectory,
                    usage
            );
        }

        private void startStreaming() throws IOException {
            Files.deleteIfExists(stdoutFile);
            Files.deleteIfExists(stderrFile);

            Thread stdoutThread = new Thread(
                    () -> consumeStream(process.getInputStream(), stdoutFile, false),
                    "codex-runner-stdout"
            );
            stdoutThread.setDaemon(true);
            stdoutThread.start();

            Thread stderrThread = new Thread(
                    () -> consumeStream(process.getErrorStream(), stderrFile, true),
                    "codex-runner-stderr"
            );
            stderrThread.setDaemon(true);
            stderrThread.start();
        }

        private void consumeStream(InputStream inputStream, Path targetFile, boolean stderr) {
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                    if (logListener != null) {
                        if (stderr) {
                            logListener.onStderr(line);
                        } else {
                            logListener.onStdout(line);
                        }
                    }
                }
                Files.write(targetFile, lines, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            } finally {
                streamLatch.countDown();
            }
        }

        private void destroyProcessTree(ProcessHandle processHandle) {
            processHandle.descendants().forEach(child -> {
                child.destroy();
                if (child.isAlive()) {
                    child.destroyForcibly();
                }
            });
        }
    }

    private Path resolveReleaseRoot() {
        Path userDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>();
        candidates.add(userDir);
        candidates.add(userDir.resolve("Dist").resolve("release"));

        for (Path candidate : candidates) {
            if (Files.exists(candidate.resolve("codex").resolve("ingenious-codex-runner.mjs"))) {
                return candidate;
            }
        }

        throw new IllegalStateException(
                "Could not find the packaged Codex runtime. Build the release with the Dist npm install profile first."
        );
    }

    private void validateRuntime(Path releaseRoot) {
        Path packageJson = releaseRoot.resolve("package.json");
        Path nodeModules = releaseRoot.resolve("node_modules").resolve("@openai").resolve("codex-sdk").resolve("package.json");
        if (!Files.exists(packageJson) || !Files.exists(nodeModules)) {
            throw new IllegalStateException(
                    "Codex runtime dependencies are missing in the release folder. Run the Dist build with -Pnpm-install."
            );
        }
    }

    private Path createRunDirectory(Project project, CodexAgentSettings settings) throws IOException {
        String promptTitle = valueOrDefault(settings.promptTitle, "Codex Run");
        String safeName = promptTitle.replaceAll("[\\\\/?:*\"|><]", "_");
        String timestamp = RUN_TIMESTAMP.format(OffsetDateTime.now());
        Path runDirectory = Paths.get(project.getLocation(), "Results", "Codex", "runs", safeName + "_" + timestamp);
        Files.createDirectories(runDirectory);
        return runDirectory;
    }

    private List<String> additionalDirectories(Path releaseRoot, String projectLocation) {
        List<String> directories = new ArrayList<>();
        String releasePath = releaseRoot.toAbsolutePath().normalize().toString();
        directories.add(releasePath);

        Path projectPath = Paths.get(projectLocation).toAbsolutePath().normalize();
        if (!projectPath.toString().equalsIgnoreCase(releasePath)) {
            directories.add(projectPath.toString());
        }

        return directories;
    }

    private String buildExecutionPrompt(String userPrompt, String projectLocation) {
        Path projectPath = Paths.get(projectLocation).toAbsolutePath().normalize();
        String relativeProjectPath = projectPath.getFileName() != null
                ? "Projects\\" + projectPath.getFileName()
                : projectLocation;

        return String.join("\n",
                "Codex execution context:",
                "- You are running from the packaged INGenious runtime root.",
                "- The current working directory already contains `ingenious-cli.bat` and `.agents`.",
                "- Resolve BDD goals from `.agents/bdd_goals`.",
                "- Pass INGenious project paths relative to the runtime root, for example `" + relativeProjectPath + "`.",
                "- On Windows, when invoking the packaged CLI launcher, prefer `cmd /c \"ingenious-cli.bat ...\"` instead of direct PowerShell execution of the `.bat` file.",
                "- Use the `ingenious-testar` skill instructions before driving TESTAR CLI commands.",
                "",
                userPrompt
        );
    }

    private String readText(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        return node != null && !node.isNull() ? node.asText("") : "";
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

}
