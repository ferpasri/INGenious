package com.ing.ide.main.testar.service;

import com.ing.datalib.component.Project;
import com.ing.ide.main.testar.TESTARDataWriter;
import com.ing.ide.main.testar.mcp.BddStepTracker;
import com.ing.ide.main.testar.mcp.Feedback;
import com.ing.ide.main.testar.service.action.ActionService;
import com.ing.ide.main.testar.service.action.PlaywrightActionService;
import com.ing.ide.main.testar.service.assertion.AssertionService;
import com.ing.ide.main.testar.service.assertion.PlaywrightAssertionService;
import com.ing.ide.main.testar.service.persistence.PersistenceService;
import com.ing.ide.main.testar.service.persistence.INGeniousPersistenceService;
import com.ing.ide.main.testar.service.session.SessionService;
import com.ing.ide.main.testar.service.session.PlaywrightSessionService;
import com.ing.ide.main.testar.service.state.StateService;
import com.ing.ide.main.testar.service.state.PlaywrightStateService;
import com.ing.ingenious.api.contract.testar.TestarBackendApi;
import com.ing.ingenious.api.contract.testar.TestarResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlaywrightTestarBackendApi implements TestarBackendApi {

    private SessionContext context;
    private PersistenceService persistenceService;
    private SessionService sessionService;
    private StateService stateService;
    private ActionService actionService;
    private AssertionService assertionService;
    private BddStepTracker bddStepTracker;

    @Override
    public TestarResult startSession(String projectPath, String bddScenarioName, String bddInstructions, String bddScenarioSource, String url) {
        try {
            Project project = new Project(projectPath);
            TESTARDataWriter dataWriter = new TESTARDataWriter(project, bddScenarioName);
            this.context = new SessionContext(dataWriter);
            this.context.setBddScenarioName(bddScenarioName != null ? bddScenarioName : "");
            this.context.setBddScenarioSource(bddScenarioSource != null ? bddScenarioSource : "");
            this.context.setRuntimeTempDir(createRuntimeTempDir());
            this.persistenceService = new INGeniousPersistenceService(dataWriter);
            this.sessionService = new PlaywrightSessionService(persistenceService);
            this.stateService = new PlaywrightStateService(persistenceService);
            this.actionService = new PlaywrightActionService(persistenceService);
            this.assertionService = new PlaywrightAssertionService(persistenceService);
            this.bddStepTracker = createBddStepTracker(bddInstructions);
        } catch (Exception e) {
            return TestarResult.failure("error", "Failed to initialize TESTAR backend: " + e.getMessage());
        }

        String initialBddStep = resolveInitialBddStep(url);
        Feedback feedback = sessionService.loadWebURL(context, initialBddStep, url);
        recordIfSuccess(initialBddStep, feedback);
        return toResult(feedback, "started");
    }

    @Override
    public TestarResult getSessionStatus() {
        if (context == null) {
            return TestarResult.failure("idle", "No TESTAR session initialized.");
        }

        boolean active = context.getSystem() != null && context.getState() != null;
        return TestarResult.success(
                active ? "active" : "initialized",
                active ? "TESTAR session is active." : "TESTAR backend is initialized but no active browser session exists.",
                buildProgressPayload()
        );
    }

    @Override
    public TestarResult getCurrentUrl() {
        return toResult(sessionService.getCurrentURL(context), "ok");
    }

    @Override
    public TestarResult navigateBack() {
        return toResult(sessionService.navigateBack(context), "ok");
    }

    @Override
    public TestarResult stopSession() {
        if (context == null) {
            return TestarResult.success("stopped", "No TESTAR session was active.", null);
        }

        persistenceService.saveExecutionSteps();
        sessionService.stop(context);
        return TestarResult.success("stopped", "TESTAR session stopped.", null);
    }

    @Override
    public TestarResult getStateInteractiveWidgets() {
        return toResult(stateService.getStateInteractiveWidgets(context), "ok");
    }

    @Override
    public TestarResult getStateImage() {
        Feedback feedback = stateService.getStateImage(context);
        if (feedback == null) {
            return TestarResult.failure("error", "No feedback returned from TESTAR backend.");
        }
        if (feedback.isIssue()) {
            return TestarResult.failure("error", feedback.toString());
        }

        try {
            String imagePath = persistStateImage(feedback.toString());
            String payload = "imagePath: " + imagePath;
            return TestarResult.success("ok", "State image written.", payload);
        } catch (IllegalArgumentException | IOException exception) {
            return TestarResult.failure("error", "Failed to persist state image: " + exception.getMessage());
        }
    }

    @Override
    public TestarResult getStateVisualText() {
        return toResult(stateService.getStateVisualText(context), "ok");
    }

    @Override
    public TestarResult executeClickAction(String bddStep, String cssSelector) {
        Feedback validation = validateBddStep(bddStep);
        if (validation != null) {
            return toResult(validation, "error");
        }

        Feedback result = actionService.executeClickAction(context, normalizedBddStep(bddStep), cssSelector);
        recordIfSuccess(bddStep, result);
        return toResult(result, "ok");
    }

    @Override
    public TestarResult executeFillAction(String bddStep, String cssSelector, String fillText) {
        Feedback validation = validateBddStep(bddStep);
        if (validation != null) {
            return toResult(validation, "error");
        }

        Feedback result = actionService.executeFillAction(context, normalizedBddStep(bddStep), cssSelector, fillText);
        recordIfSuccess(bddStep, result);
        return toResult(result, "ok");
    }

    @Override
    public TestarResult executeSelectAction(String bddStep, String cssSelector, String optionValue) {
        Feedback validation = validateBddStep(bddStep);
        if (validation != null) {
            return toResult(validation, "error");
        }

        Feedback result = actionService.executeSelectAction(context, normalizedBddStep(bddStep), cssSelector, optionValue);
        recordIfSuccess(bddStep, result);
        return toResult(result, "ok");
    }

    @Override
    public TestarResult getExecutedActions() {
        if (context != null && !context.getExecutedActionEntries().isEmpty()) {
            return TestarResult.success(
                    "ok",
                    "BDD-aware action history.",
                    buildBddAwareActionHistory()
            );
        }

        return toResult(actionService.checkExecutedActions(context), "ok");
    }

    @Override
    public TestarResult addAssert(String bddStep, String assertText) {
        Feedback validation = validateBddStep(bddStep);
        if (validation != null) {
            return toResult(validation, "error");
        }

        Feedback result = assertionService.addStepAssert(context, normalizedBddStep(bddStep), assertText);
        recordIfSuccess(bddStep, result);
        return toResult(result, "ok");
    }

    private BddStepTracker createBddStepTracker(String bddInstructions) {
        String normalizedInstructions = normalizeBddInstructions(bddInstructions);
        if (normalizedInstructions.isEmpty()) {
            return null;
        }
        return new BddStepTracker(normalizedInstructions);
    }

    private String normalizeBddInstructions(String bddInstructions) {
        if (bddInstructions == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        String[] lines = bddInstructions.split("\\r?\\n");
        for (String line : lines) {
            String normalizedLine = BddStepTracker.normalizeText(line);
            if (normalizedLine.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(normalizedLine);
        }
        return builder.toString();
    }

    private String resolveInitialBddStep(String url) {
        if (bddStepTracker != null && !bddStepTracker.getOriginalBddSteps().isEmpty()) {
            return bddStepTracker.getOriginalBddSteps().get(0);
        }

        return "Given the user navigates to the url '" + url + "'";
    }

    private Feedback validateBddStep(String bddStep) {
        if (bddStepTracker == null) {
            return null;
        }
        return bddStepTracker.validateBddStep(bddStep);
    }

    private String normalizedBddStep(String bddStep) {
        return BddStepTracker.normalizeText(bddStep);
    }

    private void recordIfSuccess(String bddStep, Feedback feedback) {
        if (bddStepTracker == null) {
            return;
        }
        if (feedback != null && !feedback.isIssue()) {
            bddStepTracker.saveExecutedBddStep(bddStep);
        }
    }

    private String buildProgressPayload() {
        if (bddStepTracker == null) {
            return "";
        }

        StringBuilder payload = new StringBuilder();
        if (context != null && context.getBddScenarioName() != null && !context.getBddScenarioName().isEmpty()) {
            payload.append("scenarioTitle: ").append(context.getBddScenarioName());
            payload.append('\n');
        }
        if (context != null && context.getBddScenarioSource() != null && !context.getBddScenarioSource().isEmpty()) {
            payload.append("scenarioSource: ").append(context.getBddScenarioSource());
            payload.append('\n');
        }
        payload.append("knownBddSteps: ").append(bddStepTracker.getOriginalBddSteps().size());
        payload.append('\n');
        payload.append("executedBddSteps: ").append(bddStepTracker.getExecutedBddSteps().size());

        String latestStep = bddStepTracker.getLatestExecutedBddStep();
        if (!latestStep.isEmpty()) {
            payload.append('\n');
            payload.append("latestValidStep: ").append(latestStep);
        }

        return payload.toString();
    }

    private Path createRuntimeTempDir() throws IOException {
        String sessionId = "session-" + UUID.randomUUID();
        Path appRoot = Path.of(System.getProperty("user.dir"));
        Path runtimeDir = appRoot.resolve("tmp").resolve("testar").resolve(sessionId);
        Files.createDirectories(runtimeDir);
        return runtimeDir;
    }

    private String persistStateImage(String base64Image) throws IOException {
        if (context == null || context.getRuntimeTempDir() == null) {
            throw new IOException("No runtime temp directory initialized for this TESTAR session.");
        }

        byte[] screenshotBytes;
        try {
            screenshotBytes = Base64.getDecoder().decode(base64Image);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Screenshot data is not valid base64.", exception);
        }

        int imageIndex = context.nextStateImageCounter();
        Path imagePath = context.getRuntimeTempDir().resolve(String.format("state-%03d.png", imageIndex));
        Files.write(imagePath, screenshotBytes);
        return imagePath.toString();
    }

    private String buildBddAwareActionHistory() {
        List<SessionContext.ExecutedActionEntry> entries = context.getExecutedActionEntries();
        Map<String, Integer> perStepCounters = new LinkedHashMap<>();
        StringBuilder payload = new StringBuilder();
        payload.append("totalExecutedActions: ").append(entries.size());

        for (int index = 0; index < entries.size(); index++) {
            SessionContext.ExecutedActionEntry entry = entries.get(index);
            String bddStep = entry.getBddStep() != null && !entry.getBddStep().isEmpty()
                    ? entry.getBddStep()
                    : "(no bdd step)";
            int stepCounter = perStepCounters.getOrDefault(bddStep, 0) + 1;
            perStepCounters.put(bddStep, stepCounter);

            payload.append('\n');
            payload.append("action").append(index + 1).append(": ");
            payload.append("bddStep='").append(bddStep).append("'");
            payload.append(" | type=").append(entry.getActionType());
            payload.append(" | selector=").append(entry.getSelector());
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                payload.append(" | value=").append(entry.getValue());
            }
            payload.append(" | stepActionIndex=").append(stepCounter);
        }

        return payload.toString();
    }

    private TestarResult toResult(Feedback feedback, String successStatus) {
        if (feedback == null) {
            return TestarResult.failure("error", "No feedback returned from TESTAR backend.");
        }

        String message = feedback.toString();
        if (feedback.isIssue()) {
            return TestarResult.failure("error", message);
        }

        return TestarResult.success(successStatus, message, message);
    }
}
