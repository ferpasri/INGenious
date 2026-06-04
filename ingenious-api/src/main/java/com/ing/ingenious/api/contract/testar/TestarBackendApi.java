package com.ing.ingenious.api.contract.testar;

public interface TestarBackendApi {

    TestarResult startSession(String projectPath, String bddScenarioName, String url);

    TestarResult getSessionStatus();

    TestarResult getCurrentUrl();

    TestarResult navigateBack();

    TestarResult stopSession();

    TestarResult getStateInteractiveWidgets();

    TestarResult getStateImage();

    TestarResult getStateVisualText();

    TestarResult executeClickAction(String bddStep, String cssSelector);

    TestarResult executeFillAction(String bddStep, String cssSelector, String fillText);

    TestarResult executeSelectAction(String bddStep, String cssSelector, String optionValue);

    TestarResult getExecutedActions();

    TestarResult addAssert(String bddStep, String assertText);
}
