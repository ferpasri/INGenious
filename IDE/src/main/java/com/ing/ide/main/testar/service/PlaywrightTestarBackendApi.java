package com.ing.ide.main.testar.service;

import com.ing.datalib.component.Project;
import com.ing.ide.main.testar.TESTARDataWriter;
import com.ing.ide.main.testar.mcp.BddStepTracker;
import com.ing.ide.main.testar.mcp.Feedback;
import com.ing.ide.main.testar.service.action.ActionService;
import com.ing.ide.main.testar.service.action.PlaywrightActionService;
import com.ing.ide.main.testar.service.assertion.AssertionService;
import com.ing.ide.main.testar.service.assertion.PlaywrightAssertionService;
import com.ing.ide.main.testar.service.persistence.INGeniousPersistenceService;
import com.ing.ide.main.testar.service.persistence.PersistenceService;
import com.ing.ide.main.testar.service.session.PlaywrightSessionService;
import com.ing.ide.main.testar.service.session.SessionService;
import com.ing.ide.main.testar.service.state.PlaywrightStateService;
import com.ing.ide.main.testar.service.state.StateService;
import com.ing.ide.main.testar.trace.AgentTraceContext;
import com.ing.ide.main.testar.trace.AgentTraceEventType;
import com.ing.ide.main.testar.trace.AgentTraceSource;
import com.ing.ide.main.testar.trace.AgentTraceStatus;
import com.ing.ide.main.testar.trace.AgentTraceSupport;
import com.ing.ide.main.testar.trace.JsonlAgentTraceRecorder;
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
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlaywrightTestarBackendApi implements TestarBackendApi {
    private static final Logger LOGGER = Logger.getLogger(
        PlaywrightTestarBackendApi.class.getName()
    );
    private SessionContext context;
    private PersistenceService persistenceService;
    private SessionService sessionService;
    private StateService stateService;
    private ActionService actionService;
    private AssertionService assertionService;
    private BddStepTracker bddStepTracker;
    private AgentTraceContext traceContext;

    @Override
    public TestarResult startSession(
        String projectPath,
        String bddScenarioName,
        String bddInstructions,
        String bddScenarioSource,
        String url
    ) {
        try {
            Project project = new Project(projectPath);
            TESTARDataWriter dataWriter = new TESTARDataWriter(project, bddScenarioName);
            this.traceContext = createTraceContext(project.getLocation());
            this.context = new SessionContext(dataWriter);
            this.context.setBddScenarioName(bddScenarioName != null ? bddScenarioName : "");
            this.context.setBddScenarioSource(bddScenarioSource != null ? bddScenarioSource : "");
            this.context.setRuntimeTempDir(createRuntimeTempDir());
            this.context.setTraceContext(traceContext);
            this.persistenceService = new INGeniousPersistenceService(dataWriter, traceContext);
            this.sessionService = new PlaywrightSessionService(persistenceService);
            this.stateService = new PlaywrightStateService(persistenceService);
            this.actionService = new PlaywrightActionService(persistenceService);
            this.assertionService = new PlaywrightAssertionService(persistenceService);
            this.bddStepTracker = createBddStepTracker(bddInstructions);
        } catch (Exception e) {
            return TestarResult.failure(
                "error",
                "Failed to initialize TESTAR backend: " + e.getMessage()
            );
        }

        String initialBddStep = resolveInitialBddStep(url);
        traceContext.append(
            AgentTraceSource.CLI,
            AgentTraceEventType.RUN_STARTED,
            AgentTraceStatus.INFO,
            "session.start",
            "TESTAR CLI session started."
        );
        return traceFeedback(
            "session.start",
            initialBddStep,
            AgentTraceSupport.arguments(
                "projectPath",
                projectPath,
                "scenarioTitle",
                bddScenarioName,
                "scenarioSource",
                bddScenarioSource,
                "url",
                url
            ),
            "started",
            () -> sessionService.loadWebURL(context, initialBddStep, url)
        );
    }

    @Override
    public TestarResult getSessionStatus() {
        if (context == null) {
            return TestarResult.failure("idle", "No TESTAR session initialized.");
        }

        boolean active = context.getSystem() != null && context.getState() != null;
        return TestarResult.success(
            active ? "active" : "initialized",
            active
                ? "TESTAR session is active."
                : "TESTAR backend is initialized but no active browser session exists.",
            buildProgressPayload()
        );
    }

    @Override
    public TestarResult getCurrentUrl() {
        return traceFeedback(
            "navigation.url",
            "",
            Map.of(),
            "ok",
            () -> sessionService.getCurrentURL(context)
        );
    }

    @Override
    public TestarResult navigateBack() {
        return traceFeedback(
            "navigation.back",
            "",
            Map.of(),
            "ok",
            () -> sessionService.navigateBack(context)
        );
    }

    @Override
    public TestarResult stopSession() {
        if (context == null) {
            return TestarResult.success("stopped", "No TESTAR session was active.", null);
        }

        persistenceService.saveExecutionSteps();
        sessionService.stop(context);
        traceContext.append(
            AgentTraceSource.CLI,
            AgentTraceEventType.RUN_FINISHED,
            AgentTraceStatus.SUCCEEDED,
            "session.stop",
            "TESTAR CLI session stopped."
        );
        traceContext.close();
        return TestarResult.success("stopped", "TESTAR session stopped.", null);
    }

    @Override
    public TestarResult getStateInteractiveWidgets() {
        return traceFeedback(
            "state.widgets",
            "",
            Map.of(),
            "ok",
            () -> stateService.getStateInteractiveWidgets(context)
        );
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
            traceContext.append(
                AgentTraceSource.TESTAR,
                AgentTraceEventType.STATE_OBSERVED,
                AgentTraceStatus.SUCCEEDED,
                "",
                "state.image",
                "",
                Map.of(),
                "State image captured.",
                Map.of(),
                0L,
                Map.of("imagePath", imagePath),
                Map.of()
            );
            return TestarResult.success("ok", "State image written.", payload);
        } catch (IllegalArgumentException | IOException exception) {
            return TestarResult.failure(
                "error",
                "Failed to persist state image: " + exception.getMessage()
            );
        }
    }

    @Override
    public TestarResult getStateVisualText() {
        return traceFeedback(
            "state.text",
            "",
            Map.of(),
            "ok",
            () -> stateService.getStateVisualText(context)
        );
    }

    @Override
    public TestarResult getStateTitle() {
        return traceFeedback(
            "state.title",
            "",
            Map.of(),
            "ok",
            () -> stateService.getStateTitle(context)
        );
    }

    @Override
    public TestarResult executeClickAction(String bddStep, String cssSelector) {
        return traceFeedback(
            "action.click",
            bddStep,
            AgentTraceSupport.arguments("selector", cssSelector),
            "ok",
            () ->
                executeValidatedAction(
                    bddStep,
                    () ->
                        actionService.executeClickAction(
                            context,
                            normalizedBddStep(bddStep),
                            cssSelector
                        )
                )
        );
    }

    @Override
    public TestarResult executeCheckAction(String bddStep, String cssSelector) {
        return traceFeedback(
            "action.check",
            bddStep,
            AgentTraceSupport.arguments("selector", cssSelector),
            "ok",
            () ->
                executeValidatedAction(
                    bddStep,
                    () ->
                        actionService.executeCheckAction(
                            context,
                            normalizedBddStep(bddStep),
                            cssSelector
                        )
                )
        );
    }

    @Override
    public TestarResult executeFillAction(String bddStep, String cssSelector, String fillText) {
        return traceFeedback(
            "action.fill",
            bddStep,
            AgentTraceSupport.arguments("selector", cssSelector, "text", fillText),
            "ok",
            () ->
                executeValidatedAction(
                    bddStep,
                    () ->
                        actionService.executeFillAction(
                            context,
                            normalizedBddStep(bddStep),
                            cssSelector,
                            fillText
                        )
                )
        );
    }

    @Override
    public TestarResult executeSelectAction(
        String bddStep,
        String cssSelector,
        String optionValue
    ) {
        return traceFeedback(
            "action.select",
            bddStep,
            AgentTraceSupport.arguments("selector", cssSelector, "optionValue", optionValue),
            "ok",
            () ->
                executeValidatedAction(
                    bddStep,
                    () ->
                        actionService.executeSelectAction(
                            context,
                            normalizedBddStep(bddStep),
                            cssSelector,
                            optionValue
                        )
                )
        );
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
        return traceFeedback(
            "assert.add",
            bddStep,
            AgentTraceSupport.arguments("text", assertText),
            "ok",
            () ->
                executeValidatedAction(
                    bddStep,
                    () ->
                        assertionService.addStepAssert(
                            context,
                            normalizedBddStep(bddStep),
                            assertText
                        )
                )
        );
    }

    @Override
    public TestarResult addTitleAssert(String bddStep, String titlePattern) {
        return traceFeedback(
            "assert.title",
            bddStep,
            AgentTraceSupport.arguments("titlePattern", titlePattern),
            "ok",
            () ->
                executeValidatedAction(
                    bddStep,
                    () ->
                        assertionService.addTitleAssert(
                            context,
                            normalizedBddStep(bddStep),
                            titlePattern
                        )
                )
        );
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

    private Feedback executeValidatedAction(String bddStep, Supplier<Feedback> action) {
        Feedback validation = validateBddStep(bddStep);
        if (validation != null) {
            return validation;
        }
        Feedback feedback = action.get();
        recordIfSuccess(bddStep, feedback);
        return feedback;
    }

    private TestarResult traceFeedback(
        String operation,
        String bddStep,
        Map<String, Object> arguments,
        String successStatus,
        Supplier<Feedback> action
    ) {
        if (traceContext == null) {
            return toResult(action.get(), successStatus);
        }

        String operationId = UUID.randomUUID().toString();
        traceContext.beginOperation(operationId);
        if ("action.fill".equals(operation)) {
            Object selector = arguments.get("selector");
            if (selector != null && selector.toString().toLowerCase().contains("password")) {
                arguments = new LinkedHashMap<>(arguments);
                arguments.put("text", "[REDACTED]");
            }
        }
        traceContext.append(
            AgentTraceSource.CLI,
            AgentTraceEventType.COMMAND_REQUESTED,
            AgentTraceStatus.STARTED,
            bddStep,
            operation,
            operationId,
            arguments,
            "TESTAR CLI operation requested.",
            Map.of(),
            0L,
            Map.of(),
            Map.of()
        );

        long startNanos = System.nanoTime();
        try {
            Feedback feedback = action.get();
            AgentTraceSupport.appendFeedback(
                traceContext,
                AgentTraceSource.TESTAR,
                bddStep,
                operation,
                feedback,
                (System.nanoTime() - startNanos) / 1_000_000L
            );
            traceContext.append(
                AgentTraceSource.TESTAR,
                AgentTraceEventType.OPERATION_COMPLETED,
                feedback != null && feedback.isIssue()
                    ? AgentTraceStatus.FAILED
                    : AgentTraceStatus.SUCCEEDED,
                bddStep,
                operation,
                operationId,
                Map.of(),
                feedback != null ? feedback.toString() : "No feedback returned.",
                Map.of(),
                (System.nanoTime() - startNanos) / 1_000_000L,
                Map.of(),
                Map.of()
            );
            return toResult(feedback, successStatus);
        } catch (RuntimeException exception) {
            traceContext.append(
                AgentTraceSource.TESTAR,
                AgentTraceEventType.RUN_ERROR,
                AgentTraceStatus.FAILED,
                bddStep,
                operation,
                operationId,
                Map.of(),
                exception.getMessage() != null
                    ? exception.getMessage()
                    : exception.getClass().getName(),
                Map.of(),
                (System.nanoTime() - startNanos) / 1_000_000L,
                Map.of(),
                Map.of()
            );
            throw exception;
        } finally {
            traceContext.endOperation();
        }
    }

    private AgentTraceContext createTraceContext(String projectLocation) {
        String runId = configuredTraceValue(
            "ingenious.testar.traceRunId",
            "INGENIOUS_AGENT_RUN_ID"
        );
        if (runId == null || runId.isBlank()) {
            runId = "cli-" + UUID.randomUUID();
        }

        Path traceDirectory;
        String configuredTraceDirectory = configuredTraceValue(
            "ingenious.testar.traceDirectory",
            "INGENIOUS_AGENT_TRACE_DIR"
        );
        String traceFileName = configuredTraceDirectory != null &&
            !configuredTraceDirectory.isBlank()
            ? "testar-trace.jsonl"
            : "trace.jsonl";
        if (configuredTraceDirectory != null && !configuredTraceDirectory.isBlank()) {
            traceDirectory = Path.of(configuredTraceDirectory);
        } else {
            traceDirectory = Path.of(projectLocation, "Results", "CLI", "traces", runId);
        }

        try {
            return new AgentTraceContext(
                runId,
                new JsonlAgentTraceRecorder(traceDirectory.resolve(traceFileName))
            );
        } catch (IOException | RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Could not initialize TESTAR trace recorder", exception);
            return new AgentTraceContext(runId, null);
        }
    }

    private String configuredTraceValue(String propertyName, String environmentName) {
        String value = System.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            value = System.getenv(environmentName);
        }
        return value;
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
        if (
            context != null &&
            context.getBddScenarioName() != null &&
            !context.getBddScenarioName().isEmpty()
        ) {
            payload.append("scenarioTitle: ").append(context.getBddScenarioName());
            payload.append('\n');
        }
        if (
            context != null &&
            context.getBddScenarioSource() != null &&
            !context.getBddScenarioSource().isEmpty()
        ) {
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
        Path imagePath = context
            .getRuntimeTempDir()
            .resolve(String.format("state-%03d.png", imageIndex));
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
