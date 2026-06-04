package com.ing.ide.main.testar.mcp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public final class BddStepTracker {

    private final List<String> originalBddStepsList;
    private final List<String> executedSteps = new ArrayList<>();

    public BddStepTracker(String bddInstructions) {
        this.originalBddStepsList = parseBddInstructionList(bddInstructions);
    }

    private List<String> parseBddInstructionList(String bddInstructions) {
        if (bddInstructions == null || bddInstructions.isBlank()) return Collections.emptyList();
        return Arrays.stream(bddInstructions.split("\\r?\\n"))
                .map(BddStepTracker::normalizeText)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    public Feedback validateBddStep(String bddStep) {
        String normalizedBddStep = normalizeText(bddStep);

        if (normalizedBddStep.isEmpty()) {
            return Feedback.issue("The provided BDD step is empty or invalid.");
        }

        if (!isOriginalBddInstruction(normalizedBddStep)) {
            return Feedback.issue("The provided BDD step does not seem to match with original BDD instructions.");
        }

        // If the step is not the latest and was already executed, the mapping is trying to be done with an old previous step
        if (!isLatestBddStep(normalizedBddStep) && hasExecutedBddStep(normalizedBddStep)) {
            String message = String.format(
                    "The provided BDD step '%s' is not the current or a new step. " +
                    "This may create a mismatched BDD-action map. " + 
                    "Please refine the BDD step or just continue with other appropiated BDD steps.",
                    normalizedBddStep
            );
            return Feedback.issue(message);
        }

        return null;
    }

    public void saveExecutedBddStep(String bddStep) {
        String normalizedBddStep = normalizeText(bddStep);
        if (!normalizedBddStep.isEmpty() && !hasExecutedBddStep(normalizedBddStep)) {
            executedSteps.add(normalizedBddStep);
        }
    }

    public List<String> getOriginalBddSteps() {
        return Collections.unmodifiableList(originalBddStepsList);
    }

    public List<String> getExecutedBddSteps() {
        return Collections.unmodifiableList(executedSteps);
    }

    public String getLatestExecutedBddStep() {
        if (executedSteps.isEmpty()) {
            return "";
        }
        return executedSteps.get(executedSteps.size() - 1);
    }

    private boolean hasExecutedBddStep(String bddStep) {
        return executedSteps.stream().anyMatch(s -> s.equalsIgnoreCase(bddStep));
    }

    private boolean isLatestBddStep(String bddStep) {
        if (executedSteps.isEmpty()) return false;
        return executedSteps.get(executedSteps.size() - 1).equalsIgnoreCase(bddStep);
    }

    private boolean isOriginalBddInstruction(String bddStep) {
        return originalBddStepsList.stream().anyMatch(s -> s.equalsIgnoreCase(bddStep));
    }

    public static String normalizeText(String text) {
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

}
