package com.ing.ide.main.testar.service.persistence;

import com.ing.ide.main.testar.playwright.system.PlaywrightState;
import com.ing.ide.main.testar.playwright.system.PlaywrightWidget;
import com.ing.ide.main.testar.trace.ArtifactPersistenceResult;
import com.microsoft.playwright.Page;
import org.testar.monkey.alayer.Action;

public interface PersistenceService {
    ArtifactPersistenceResult recordLoadWebUrl(String bddStep, String url);

    ArtifactPersistenceResult recordNavigateBack();

    void persistWidgetObject(PlaywrightWidget widget, Page page);

    ArtifactPersistenceResult recordActionStep(
        String bddStep,
        PlaywrightState state,
        Action action,
        Page page
    );

    ArtifactPersistenceResult recordAssertStep(
        String bddStep,
        PlaywrightState state,
        String assertText
    );

    ArtifactPersistenceResult recordTitleAssertStep(String bddStep, String titlePattern);

    void saveExecutionSteps();
}
