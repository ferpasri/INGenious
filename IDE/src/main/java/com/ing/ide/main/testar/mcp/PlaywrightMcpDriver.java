package com.ing.ide.main.testar.mcp;

import com.ing.datalib.component.Project;
import com.ing.ide.main.testar.TESTARDataWriter;
import com.ing.ide.main.testar.service.SessionContext;
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
import com.ing.ide.main.testar.trace.JsonlAgentTraceRecorder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlaywrightMcpDriver implements McpInterface {
    private static final Logger LOGGER = Logger.getLogger(PlaywrightMcpDriver.class.getName());

    private final SessionContext context;
    private final PersistenceService persistenceService;
    private final SessionService sessionService;
    private final StateService stateService;
    private final ActionService actionService;
    private final AssertionService assertionService;
    private final AgentTraceContext traceContext;
    private final Path tracePath;

    public PlaywrightMcpDriver(Project project, String bddScenarioName) {
        // Initialize the data writer for saving OR objects and steps
        TESTARDataWriter dataWriter = new TESTARDataWriter(project, bddScenarioName);
        this.context = new SessionContext(dataWriter);
        String traceRunId = "mcp-" + UUID.randomUUID();
        this.tracePath =
            Path.of(project.getLocation(), "Results", "MCP", "traces", traceRunId, "trace.jsonl");
        AgentTraceContext createdTraceContext;
        try {
            createdTraceContext =
                new AgentTraceContext(traceRunId, new JsonlAgentTraceRecorder(tracePath));
        } catch (IOException | RuntimeException exception) {
            LOGGER.log(Level.WARNING, "Could not initialize MCP trace recorder", exception);
            createdTraceContext = new AgentTraceContext(traceRunId, null);
        }
        this.traceContext = createdTraceContext;
        this.context.setTraceContext(traceContext);
        this.persistenceService = new INGeniousPersistenceService(dataWriter, traceContext);
        this.sessionService = new PlaywrightSessionService(persistenceService);
        this.stateService = new PlaywrightStateService(persistenceService);
        this.actionService = new PlaywrightActionService(persistenceService);
        this.assertionService = new PlaywrightAssertionService(persistenceService);
    }

    public String getRunName() {
        return context.getDataWriter().getRunName();
    }

    public AgentTraceContext getTraceContext() {
        return traceContext;
    }

    public String getTracePath() {
        return "traces/" + traceContext.getRunId() + "/trace.html";
    }

    @Override
    public Feedback loadWebURL(String bddStep, String url) {
        return sessionService.loadWebURL(context, bddStep, url);
    }

    @Override
    public Feedback getCurrentURL() {
        return sessionService.getCurrentURL(context);
    }

    @Override
    public Feedback navigateBack() {
        return sessionService.navigateBack(context);
    }

    @Override
    public Feedback getStateInteractiveWidgets() {
        return stateService.getStateInteractiveWidgets(context);
    }

    @Override
    public Feedback executeClickAction(String bddStep, String rawCssSelector) {
        return actionService.executeClickAction(context, bddStep, rawCssSelector);
    }

    @Override
    public Feedback executeCheckAction(String bddStep, String rawCssSelector) {
        return actionService.executeCheckAction(context, bddStep, rawCssSelector);
    }

    @Override
    public Feedback executeFillAction(String bddStep, String rawCssSelector, String fillText) {
        return actionService.executeFillAction(context, bddStep, rawCssSelector, fillText);
    }

    @Override
    public Feedback executeSelectAction(String bddStep, String rawCssSelector, String optionValue) {
        return actionService.executeSelectAction(context, bddStep, rawCssSelector, optionValue);
    }

    @Override
    public Feedback checkExecutedActions() {
        return actionService.checkExecutedActions(context);
    }

    @Override
    public Feedback getStateImage() {
        return stateService.getStateImage(context);
    }

    public Feedback getStateVisualText() {
        return stateService.getStateVisualText(context);
    }

    @Override
    public Feedback getStateTitle() {
        return stateService.getStateTitle(context);
    }

    @Override
    public Feedback addStepAssert(String bddStep, String assertText) {
        return assertionService.addStepAssert(context, bddStep, assertText);
    }

    @Override
    public Feedback addTitleAssert(String bddStep, String titlePattern) {
        return assertionService.addTitleAssert(context, bddStep, titlePattern);
    }

    @Override
    public void stopTestExecution() {
        // At the end of the generated sequence, save the generated INGenious testCase
        persistenceService.saveExecutionSteps();
        sessionService.stop(context);
    }
}
