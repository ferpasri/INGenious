package com.ing.engine.cli.commands.testar;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TestarBddGoalLoader {

    private static final String SCENARIO_PREFIX = "Scenario:";

    private TestarBddGoalLoader() {
    }

    public static ResolvedBddGoal resolve(String bddGoalId, String bddFilePath, String fallbackScenarioName) {
        if (bddGoalId != null && !bddGoalId.trim().isEmpty() && bddFilePath != null && !bddFilePath.trim().isEmpty()) {
            throw new IllegalArgumentException("Use either --bdd-goal or --bdd-file, not both.");
        }

        if (bddGoalId != null && !bddGoalId.trim().isEmpty()) {
            return loadFromPath(resolveGoalPath(bddGoalId), fallbackScenarioName);
        }

        if (bddFilePath != null && !bddFilePath.trim().isEmpty()) {
            return loadFromPath(resolveFilePath(bddFilePath), fallbackScenarioName);
        }

        return new ResolvedBddGoal(fallbackScenarioName, "", null);
    }

    private static ResolvedBddGoal loadFromPath(Path path, String fallbackScenarioName) {
        if (path == null || !Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("BDD scenario file not found: " + path);
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read BDD scenario file: " + path, exception);
        }

        String scenarioName = "";
        List<String> bddSteps = new ArrayList<>();

        for (String line : lines) {
            String normalizedLine = normalizeText(line);
            if (normalizedLine.isEmpty()) {
                continue;
            }

            if (scenarioName.isEmpty() && normalizedLine.startsWith(SCENARIO_PREFIX)) {
                scenarioName = normalizeText(normalizedLine.substring(SCENARIO_PREFIX.length()));
                continue;
            }

            bddSteps.add(normalizedLine);
        }

        if (scenarioName.isEmpty()) {
            scenarioName = fallbackScenarioName != null && !fallbackScenarioName.trim().isEmpty()
                    ? fallbackScenarioName.trim()
                    : deriveScenarioNameFromFile(path);
        }

        return new ResolvedBddGoal(
                scenarioName,
                String.join("\n", bddSteps),
                path
        );
    }

    private static Path resolveGoalPath(String bddGoalId) {
        String normalizedGoalId = bddGoalId.replace('/', java.io.File.separatorChar)
                .replace('\\', java.io.File.separatorChar);
        if (!normalizedGoalId.endsWith(".md")) {
            normalizedGoalId = normalizedGoalId + ".md";
        }

        Path root = findBddGoalsRoot(Path.of(System.getProperty("user.dir")));
        if (root == null) {
            throw new IllegalArgumentException(
                    "Unable to locate '.agents/bdd_goals' in the current runtime root. "
                            + "Package '.agents/bdd_goals' into the release or run from a workspace root that contains it."
            );
        }

        return root.resolve(normalizedGoalId).normalize();
    }

    private static Path resolveFilePath(String bddFilePath) {
        Path path = Path.of(bddFilePath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return Path.of(System.getProperty("user.dir")).resolve(path).normalize();
    }

    private static Path findBddGoalsRoot(Path start) {
        Path normalizedStart = start.normalize();
        Path candidate = normalizedStart.resolve(".agents").resolve("bdd_goals");
        if (Files.isDirectory(candidate)) {
            return candidate;
        }

        Path parent = normalizedStart.getParent();
        if (parent != null) {
            Path parentCandidate = parent.resolve(".agents").resolve("bdd_goals");
            if (Files.isDirectory(parentCandidate)) {
                return parentCandidate;
            }
        }

        return null;
    }

    private static String deriveScenarioNameFromFile(Path path) {
        String fileName = path.getFileName().toString();
        if (fileName.endsWith(".md")) {
            fileName = fileName.substring(0, fileName.length() - 3);
        }
        return fileName.replace('_', ' ');
    }

    private static String normalizeText(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace('\u2018', '\'')
                .replace('\u2019', '\'')
                .replace('\u201C', '\'')
                .replace('\u201D', '\'')
                .trim();
    }

    public static final class ResolvedBddGoal {

        private final String scenarioName;
        private final String bddInstructions;
        private final Path sourcePath;

        public ResolvedBddGoal(String scenarioName, String bddInstructions, Path sourcePath) {
            this.scenarioName = scenarioName;
            this.bddInstructions = bddInstructions;
            this.sourcePath = sourcePath;
        }

        public String getScenarioName() {
            return scenarioName;
        }

        public String getBddInstructions() {
            return bddInstructions;
        }

        public Path getSourcePath() {
            return sourcePath;
        }
    }
}
