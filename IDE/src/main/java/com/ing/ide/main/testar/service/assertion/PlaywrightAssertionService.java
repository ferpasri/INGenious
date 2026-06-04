package com.ing.ide.main.testar.service.assertion;

import com.ing.ide.main.testar.mcp.Feedback;
import com.ing.ide.main.testar.service.SessionContext;
import com.ing.ide.main.testar.service.persistence.PersistenceService;
import com.microsoft.playwright.Locator;

public class PlaywrightAssertionService implements AssertionService {

    private final PersistenceService persistenceService;

    public PlaywrightAssertionService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Override
    public Feedback addStepAssert(SessionContext context, String bddStep, String assertText) {
        if (context.getState() == null) {
            return Feedback.issue("No web state-page initialized.");
        }

        // Verify that the LLM assertText can be used as locator for assertion
        Locator locator = context.getState().getPage().locator("text=" + assertText);
        if (locator.count() == 0) {
            return Feedback.issue("The provided assert text to be used as locator does not match with any GUI web element. " +
                    "Try again with a correct text locator.");
        } else if (locator.count() > 1) {
            return Feedback.issue("The provided assert text locator is not unique because matches more than one GUI web element");
        } else if (!locator.first().isVisible()) {
            return Feedback.issue("The assert text locator is correct but the GUI web element is not visible. " +
                    "Try again with a correct text locator.");
        }

        // Save the execution steps when we have valid asserts for the BDD instructions
        persistenceService.recordAssertStep(bddStep, context.getState(), assertText);
        persistenceService.saveExecutionSteps();

        return Feedback.validContext("Assertion created successfully!");
    }
}
