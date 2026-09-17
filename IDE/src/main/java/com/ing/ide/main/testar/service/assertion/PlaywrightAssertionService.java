package com.ing.ide.main.testar.service.assertion;

import com.ing.ide.main.testar.mcp.Feedback;
import com.ing.ide.main.testar.service.SessionContext;
import com.ing.ide.main.testar.service.persistence.PersistenceService;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.PlaywrightException;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class PlaywrightAssertionService implements AssertionService {
    private final PersistenceService persistenceService;

    public PlaywrightAssertionService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Override
    public Feedback addStepAssert(SessionContext context, String bddStep, String assertText) {
        if (context.getState() == null) {
            return Feedback.issue(
                Feedback.Code.STATE_NOT_INITIALIZED,
                "No web state-page initialized."
            );
        }

        // Verify that the LLM assertText can be used as locator for assertion
        Locator locator = context.getState().getPage().locator("text=" + assertText);
        if (locator.count() == 0) {
            return Feedback.issue(
                Feedback.Code.ASSERT_TEXT_NOT_FOUND,
                "The provided assert text to be used as locator does not match with any GUI web element. " +
                "Try again with a correct text locator."
            );
        } else if (locator.count() > 1) {
            return Feedback.issue(
                Feedback.Code.ASSERT_TEXT_NOT_UNIQUE,
                "The provided assert text locator is not unique because matches more than one GUI web element"
            );
        } else if (!locator.first().isVisible()) {
            return Feedback.issue(
                Feedback.Code.ASSERT_TEXT_NOT_VISIBLE,
                "The assert text locator is correct but the GUI web element is not visible. " +
                "Try again with a correct text locator."
            );
        }

        // Save the execution steps when we have valid asserts for the BDD instructions
        persistenceService.recordAssertStep(bddStep, context.getState(), assertText);
        persistenceService.saveExecutionSteps();

        return Feedback.validContext("Assertion created successfully!");
    }

    @Override
    public Feedback addTitleAssert(SessionContext context, String bddStep, String titlePattern) {
        if (context.getState() == null) {
            return Feedback.issue(
                Feedback.Code.STATE_NOT_INITIALIZED,
                "No web state-page initialized."
            );
        }

        Pattern expectedTitlePattern;
        try {
            if (titlePattern == null || titlePattern.isBlank()) {
                throw new PatternSyntaxException("Title pattern is empty", "", -1);
            }
            expectedTitlePattern = Pattern.compile(titlePattern);
        } catch (PatternSyntaxException exception) {
            return Feedback.issue(
                Feedback.Code.ASSERT_TITLE_PATTERN_INVALID,
                "The provided page title pattern is invalid: " + exception.getDescription()
            );
        }

        try {
            String currentTitle = context.getState().getPage().title();
            if (!expectedTitlePattern.matcher(currentTitle).find()) {
                return Feedback.issue(
                    Feedback.Code.ASSERT_TITLE_MISMATCH,
                    "The current page title does not match the provided title pattern. " +
                    "Current title: " +
                    currentTitle
                );
            }
        } catch (PlaywrightException exception) {
            return Feedback.issue(
                Feedback.Code.STATE_TITLE_COLLECTION_FAILED,
                "Trying to obtain the current page title: " + exception.getMessage()
            );
        }

        persistenceService.recordTitleAssertStep(bddStep, titlePattern);
        persistenceService.saveExecutionSteps();

        return Feedback.validContext("Page title assertion created successfully!");
    }
}
