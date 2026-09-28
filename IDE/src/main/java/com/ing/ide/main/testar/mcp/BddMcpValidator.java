package com.ing.ide.main.testar.mcp;

/**
 * Apply the BDD-step validation/tracking before delegating to the technical MCP driver.
 */
public final class BddMcpValidator implements McpInterface {
    private final McpInterface mcpDriver;
    private final BddStepTracker tracker;

    public BddMcpValidator(McpInterface mcpDriver, BddStepTracker tracker) {
        this.mcpDriver = mcpDriver;
        this.tracker = tracker;
    }

    @Override
    public Feedback loadWebURL(String bddStep, String url) {
        Feedback validation = tracker.validateBddStep(bddStep);
        if (validation != null) return validation;

        Feedback result = mcpDriver.loadWebURL(bddStep, url);
        recordIfSuccess(bddStep, result);
        return result;
    }

    @Override
    public Feedback getCurrentURL() {
        return mcpDriver.getCurrentURL();
    }

    @Override
    public Feedback navigateBack() {
        return mcpDriver.navigateBack();
    }

    @Override
    public Feedback getStateInteractiveWidgets() {
        return mcpDriver.getStateInteractiveWidgets();
    }

    @Override
    public Feedback executeClickAction(String bddStep, String cssSelector) {
        Feedback validation = tracker.validateBddStep(bddStep);
        if (validation != null) return validation;

        Feedback result = mcpDriver.executeClickAction(bddStep, cssSelector);
        recordIfSuccess(bddStep, result);
        return result;
    }

    @Override
    public Feedback executeCheckAction(String bddStep, String cssSelector) {
        Feedback validation = tracker.validateBddStep(bddStep);
        if (validation != null) return validation;

        Feedback result = mcpDriver.executeCheckAction(bddStep, cssSelector);
        recordIfSuccess(bddStep, result);
        return result;
    }

    @Override
    public Feedback executeFillAction(String bddStep, String cssSelector, String fillText) {
        Feedback validation = tracker.validateBddStep(bddStep);
        if (validation != null) return validation;

        Feedback result = mcpDriver.executeFillAction(bddStep, cssSelector, fillText);
        recordIfSuccess(bddStep, result);
        return result;
    }

    @Override
    public Feedback executeSelectAction(String bddStep, String cssSelector, String optionValue) {
        Feedback validation = tracker.validateBddStep(bddStep);
        if (validation != null) return validation;

        Feedback result = mcpDriver.executeSelectAction(bddStep, cssSelector, optionValue);
        recordIfSuccess(bddStep, result);
        return result;
    }

    @Override
    public Feedback checkExecutedActions() {
        return mcpDriver.checkExecutedActions();
    }

    @Override
    public Feedback getStateImage() {
        return mcpDriver.getStateImage();
    }

    @Override
    public Feedback getStateVisualText() {
        return mcpDriver.getStateVisualText();
    }

    @Override
    public Feedback getStateTitle() {
        return mcpDriver.getStateTitle();
    }

    @Override
    public Feedback addStepAssert(String bddStep, String assertText) {
        Feedback validation = tracker.validateBddStep(bddStep);
        if (validation != null) return validation;

        Feedback result = mcpDriver.addStepAssert(bddStep, assertText);
        recordIfSuccess(bddStep, result);
        return result;
    }

    @Override
    public Feedback addTitleAssert(String bddStep, String titlePattern) {
        Feedback validation = tracker.validateBddStep(bddStep);
        if (validation != null) return validation;

        Feedback result = mcpDriver.addTitleAssert(bddStep, titlePattern);
        recordIfSuccess(bddStep, result);
        return result;
    }

    @Override
    public void stopTestExecution() {
        mcpDriver.stopTestExecution();
    }

    private void recordIfSuccess(String bddStep, Feedback result) {
        if (result != null && !result.isIssue()) {
            tracker.saveExecutedBddStep(bddStep);
        }
    }
}
