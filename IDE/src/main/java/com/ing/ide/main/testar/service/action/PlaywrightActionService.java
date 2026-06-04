package com.ing.ide.main.testar.service.action;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.ing.ide.main.testar.mcp.Feedback;
import com.ing.ide.main.testar.playwright.actions.PlaywrightClick;
import com.ing.ide.main.testar.playwright.actions.PlaywrightFill;
import com.ing.ide.main.testar.playwright.actions.PlaywrightSelect;
import com.ing.ide.main.testar.playwright.system.PlaywrightWidget;
import com.ing.ide.main.testar.service.SessionContext;
import com.ing.ide.main.testar.service.persistence.PersistenceService;

public class PlaywrightActionService implements ActionService {

    private final PersistenceService persistenceService;

    public PlaywrightActionService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Override
    public Feedback executeClickAction(SessionContext context, String bddStep, String rawCssSelector) {
        if (context.getState() == null) {
            return Feedback.issue("No web state-page initialized.");
        }

        addInfoLog("rawCssSelector: " + rawCssSelector);

        String cssSelector = normalizeCssSelector(rawCssSelector);

        if (cssSelector == null || cssSelector.trim().isEmpty()) {
            addSevereLog("ISSUE: Invalid CSS selector: " + rawCssSelector);
            return Feedback.issue("Invalid CSS selector: " + rawCssSelector);
        }

        try {
            addInfoLog("Normalized CSS Selector: " + cssSelector);

            PlaywrightWidget widget = context.getState().getWidgetFromCssSelector(cssSelector);

            if (widget == null) {
                addSevereLog("ISSUE: No matching element found for CSS selector: " + cssSelector);
                return Feedback.issue("No matching element found for CSS selector: " + cssSelector);
            }

            PlaywrightClick clickAction = new PlaywrightClick(widget);
            persistenceService.recordActionStep(bddStep, context.getState(), clickAction, context.getState().getPage());

            clickAction.run(context.getSystem(), context.getState(), 0);

            String actionDescription = "Executed Click Action in the widget " + cssSelector;
            context.getExecutedActions().add(actionDescription);

            return Feedback.validContext(actionDescription);
        } catch (Exception e) {
            addSevereLog("Failed to execute action for selector: " + cssSelector + " - " + e.getMessage());
            return Feedback.issue("Executing a click action: " + e.getMessage());
        }
    }

    @Override
    public Feedback executeFillAction(SessionContext context, String bddStep, String rawCssSelector, String fillText) {
        if (context.getState() == null) {
            return Feedback.issue("No web state-page initialized.");
        }

        addInfoLog("rawCssSelector: " + rawCssSelector);

        String cssSelector = normalizeCssSelector(rawCssSelector);

        if (cssSelector == null || cssSelector.trim().isEmpty()) {
            addSevereLog("ISSUE: Invalid CSS selector: " + rawCssSelector);
            return Feedback.issue("Invalid CSS selector: " + rawCssSelector);
        }

        try {
            addInfoLog("Normalized CSS Selector: " + cssSelector);

            PlaywrightWidget widget = context.getState().getWidgetFromCssSelector(cssSelector);

            if (widget == null) {
                addSevereLog("ISSUE: No matching element found for CSS selector: " + cssSelector);
                return Feedback.issue("No matching element found for CSS selector: " + cssSelector);
            }

            PlaywrightFill fillAction = new PlaywrightFill(widget, fillText);
            persistenceService.recordActionStep(bddStep, context.getState(), fillAction, context.getState().getPage());

            fillAction.run(context.getSystem(), context.getState(), 0);

            String actionDescription = "Executed Fill Action " + fillText + " in the widget " + cssSelector;
            context.getExecutedActions().add(actionDescription);

            return Feedback.validContext(actionDescription);
        } catch (Exception e) {
            addSevereLog("Failed to execute action for selector: " + cssSelector + " - " + e.getMessage());
            return Feedback.issue("Executing a fill action: " + e.getMessage());
        }
    }

    @Override
    public Feedback executeSelectAction(SessionContext context, String bddStep, String rawCssSelector, String optionValue) {
        if (context.getState() == null) {
            return Feedback.issue("No web state-page initialized.");
        }

        addInfoLog("rawCssSelector: " + rawCssSelector);

        String cssSelector = normalizeCssSelector(rawCssSelector);

        if (cssSelector == null || cssSelector.trim().isEmpty()) {
            addSevereLog("ISSUE: Invalid CSS selector: " + rawCssSelector);
            return Feedback.issue("Invalid CSS selector: " + rawCssSelector);
        }

        try {
            addInfoLog("Normalized CSS Selector: " + cssSelector);

            PlaywrightWidget widget = context.getState().getWidgetFromCssSelector(cssSelector);

            if (widget == null) {
                addSevereLog("ISSUE: No matching element found for CSS selector: " + cssSelector);
                return Feedback.issue("No matching element found for CSS selector: " + cssSelector);
            }

            PlaywrightSelect selectAction = new PlaywrightSelect(widget, optionValue);
            persistenceService.recordActionStep(bddStep, context.getState(), selectAction, context.getState().getPage());

            selectAction.run(context.getSystem(), context.getState(), 0);

            String actionDescription = "Select value " + optionValue + " in the widget " + cssSelector;
            context.getExecutedActions().add(actionDescription);

            return Feedback.validContext(actionDescription);
        } catch (Exception e) {
            addSevereLog("Failed to execute select action for selector: " + cssSelector + " - " + e.getMessage());
            return Feedback.issue("Executing a select action: " + e.getMessage());
        }
    }

    @Override
    public Feedback checkExecutedActions(SessionContext context) {
        if (context.getExecutedActions().isEmpty()) {
            return Feedback.validContext("No executed actions yet!");
        }

        return Feedback.validContext(String.join(", ", context.getExecutedActions()));
    }

    private String normalizeCssSelector(String rawSelector) {
        if (rawSelector == null) {
            return null;
        }

        // 1. Unescape common over-escaped characters
        String normalized = rawSelector
                .replaceAll("\\\\/", "/")
                .replaceAll("\\\\:", ":")
                .replaceAll("\\\\'", "'")
                .replaceAll("\\\\\"", "\"")
                .replaceAll("\\\\\\\\", "\\\\");

        // 2. Trim and basic cleanup
        normalized = normalized.trim();

        // 3. Optional: Basic sanity check (e.g., must start with . or # or tag)
        if (!normalized.matches("^[.#\\[]?.+")) {
            return null;
        }

        return normalized;
    }

    private void addInfoLog(String msg) {
        Logger.getLogger(PlaywrightActionService.class.getName()).log(Level.INFO, msg);
    }

    private void addSevereLog(String msg) {
        Logger.getLogger(PlaywrightActionService.class.getName()).log(Level.SEVERE, msg);
    }
}
