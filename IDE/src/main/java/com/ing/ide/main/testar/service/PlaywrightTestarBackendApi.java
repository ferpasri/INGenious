package com.ing.ide.main.testar.service;

import com.ing.datalib.component.Project;
import com.ing.ide.main.testar.TESTARDataWriter;
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

public class PlaywrightTestarBackendApi implements TestarBackendApi {

    private SessionContext context;
    private PersistenceService persistenceService;
    private SessionService sessionService;
    private StateService stateService;
    private ActionService actionService;
    private AssertionService assertionService;

    @Override
    public TestarResult startSession(String projectPath, String bddScenarioName, String url) {
        try {
            Project project = new Project(projectPath);
            TESTARDataWriter dataWriter = new TESTARDataWriter(project, bddScenarioName);
            this.context = new SessionContext(dataWriter);
            this.persistenceService = new INGeniousPersistenceService(dataWriter);
            this.sessionService = new PlaywrightSessionService(persistenceService);
            this.stateService = new PlaywrightStateService(persistenceService);
            this.actionService = new PlaywrightActionService(persistenceService);
            this.assertionService = new PlaywrightAssertionService(persistenceService);
        } catch (Exception e) {
            return TestarResult.failure("error", "Failed to initialize TESTAR backend: " + e.getMessage());
        }

        Feedback feedback = sessionService.loadWebURL(context, "Given the user navigates to the url '" + url + "'", url);
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
                null
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
        return toResult(stateService.getStateImage(context), "ok");
    }

    @Override
    public TestarResult getStateVisualText() {
        return toResult(stateService.getStateVisualText(context), "ok");
    }

    @Override
    public TestarResult executeClickAction(String bddStep, String cssSelector) {
        return toResult(actionService.executeClickAction(context, bddStep, cssSelector), "ok");
    }

    @Override
    public TestarResult executeFillAction(String bddStep, String cssSelector, String fillText) {
        return toResult(actionService.executeFillAction(context, bddStep, cssSelector, fillText), "ok");
    }

    @Override
    public TestarResult executeSelectAction(String bddStep, String cssSelector, String optionValue) {
        return toResult(actionService.executeSelectAction(context, bddStep, cssSelector, optionValue), "ok");
    }

    @Override
    public TestarResult getExecutedActions() {
        return toResult(actionService.checkExecutedActions(context), "ok");
    }

    @Override
    public TestarResult addAssert(String bddStep, String assertText) {
        return toResult(assertionService.addStepAssert(context, bddStep, assertText), "ok");
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
