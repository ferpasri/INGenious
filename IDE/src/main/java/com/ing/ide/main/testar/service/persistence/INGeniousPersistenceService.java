package com.ing.ide.main.testar.service.persistence;

import com.ing.ide.main.testar.TESTARDataWriter;
import com.ing.ide.main.testar.playwright.system.PlaywrightState;
import com.ing.ide.main.testar.playwright.system.PlaywrightWidget;
import com.microsoft.playwright.Page;
import org.testar.monkey.alayer.Action;

public class INGeniousPersistenceService implements PersistenceService {

    private final TESTARDataWriter dataWriter;

    public INGeniousPersistenceService(TESTARDataWriter dataWriter) {
        this.dataWriter = dataWriter;
    }

    @Override
    public void recordLoadWebUrl(String bddStep, String url) {
        dataWriter.addAbstractTestStep(
                bddStep,
                "Browser",
                "Open the Url [<Data>] in the Browser",
                "Open",
                "@".concat(url),
                ""
        );
    }

    @Override
    public void recordNavigateBack() {
        dataWriter.addConcreteTestStep(
                "Browser",
                "Navigate to the previous page in history",
                "GoBack",
                "",
                ""
        );
    }

    @Override
    public void persistWidgetObject(PlaywrightWidget widget, Page page) {
        dataWriter.addWidgetObject(widget, page);
    }

    @Override
    public void recordActionStep(String bddStep, PlaywrightState state, Action action, Page page) {
        dataWriter.addActionTestStep(bddStep, state, action, page);
    }

    @Override
    public void recordAssertStep(String bddStep, PlaywrightState state, String assertText) {
        dataWriter.addAssertTestStep(bddStep, state, assertText);
    }

    @Override
    public void saveExecutionSteps() {
        dataWriter.saveExecutionSteps();
    }
}
