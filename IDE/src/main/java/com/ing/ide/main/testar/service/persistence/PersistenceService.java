package com.ing.ide.main.testar.service.persistence;

import com.ing.ide.main.testar.playwright.system.PlaywrightState;
import com.ing.ide.main.testar.playwright.system.PlaywrightWidget;
import com.microsoft.playwright.Page;
import org.testar.monkey.alayer.Action;

public interface PersistenceService {

    void recordLoadWebUrl(String bddStep, String url);

    void recordNavigateBack();

    void persistWidgetObject(PlaywrightWidget widget, Page page);

    void recordActionStep(String bddStep, PlaywrightState state, Action action, Page page);

    void recordAssertStep(String bddStep, PlaywrightState state, String assertText);

    void saveExecutionSteps();
}
