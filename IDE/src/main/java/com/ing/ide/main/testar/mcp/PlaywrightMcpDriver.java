package com.ing.ide.main.testar.mcp;

import com.ing.datalib.component.Project;
import com.ing.ide.main.testar.TESTARDataWriter;
import com.ing.ide.main.testar.playwright.actions.PlaywrightClick;
import com.ing.ide.main.testar.playwright.actions.PlaywrightFill;
import com.ing.ide.main.testar.playwright.actions.PlaywrightSelect;
import com.ing.ide.main.testar.playwright.system.PlaywrightSUT;
import com.ing.ide.main.testar.playwright.system.PlaywrightState;
import com.ing.ide.main.testar.playwright.system.PlaywrightTags;
import com.ing.ide.main.testar.playwright.system.PlaywrightWidget;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

public class PlaywrightMcpDriver implements McpInterface {

    private final TESTARDataWriter dataWriter;

    private PlaywrightSUT system;
    private PlaywrightState state;

    private final List<String> executedAction = new ArrayList<>();

    public PlaywrightMcpDriver(Project project, String bddScenarioName) {
        // Initialize the data writer for saving OR objects and steps
        this.dataWriter = new TESTARDataWriter(project, bddScenarioName);
    }

    @Override
    public Feedback loadWebURL(String bddStep, String url){
        try {
            this.system = new PlaywrightSUT(url);
            this.state = new PlaywrightState(this.system);
        } catch (Exception e) {
            addSevereLog("Failed to run PlaywrightSUT with URL: " + url);
            addSevereLog(e.getMessage());
            return Feedback.issue("Loading the Web URL: " + e.getMessage());
        }

        // Add browser control test step into INGenious
        dataWriter.addAbstractTestStep(
                bddStep,
                "Browser",
                "Open the Url [<Data>] in the Browser",
                "Open",
                "@".concat(url),
                ""
        );

        return Feedback.validContext("Web URL loaded successfully!");
    }

    @Override
    public Feedback getCurrentURL() {
        if (this.state == null) return Feedback.issue("No web state-page initialized.");

        String url = this.state.getPage().url();
        if (url == null || url.isEmpty()) return Feedback.issue("No web url available.");

        return Feedback.validContext(url);
    }

    @Override
    public Feedback navigateBack() {
        if (this.state == null) return Feedback.issue("No web state-page initialized.");

        Response response = this.state.getPage().goBack();
        if (response == null) return Feedback.issue("Cannot navigate back - no previous page.");

        // Add browser control test step into INGenious
        dataWriter.addConcreteTestStep(
                "Browser",
                "Navigate to the previous page in history",
                "GoBack",
                "",
                ""
        );

        return Feedback.validContext(String.format("Success navigating back to '%s'", response.url()));
    }

    @Override
    public Feedback getStateInteractiveWidgets() {
        if (this.state == null) return Feedback.issue("No web state-page initialized.");

        List<String> widgetsContext = new ArrayList<>();

        try {
            state = new PlaywrightState(system);

            List<PlaywrightWidget> stateWidgets = state.getInteractiveWidgets();

            for (PlaywrightWidget widget : stateWidgets) {

                // For widgets with CSS locators
                if(!widget.get(PlaywrightTags.WebLocatorCSS, "").isEmpty()
                        && !isExternalLink(state, widget.get(PlaywrightTags.WebHref, ""))) {

                    // Prepare the web widget context to be sent to the AI agent
                    Map<String, String> widgetInfo = new LinkedHashMap<>();

                    widgetInfo.put("isModal", String.valueOf(widget.get(PlaywrightTags.WebIsModal, false)));

                    widgetInfo.put("css", widget.get(PlaywrightTags.WebLocatorCSS));
                    widgetInfo.put("role", widget.get(PlaywrightTags.WebTagName));

                    widgetInfo.put("placeholder", widget.get(PlaywrightTags.WebLocatorPlaceholder));
                    widgetInfo.put("label", widget.get(PlaywrightTags.WebLocatorLabel));
                    widgetInfo.put("alttext", widget.get(PlaywrightTags.WebLocatorAltText));

                    // For select elements list the available options
                    if ("select".equalsIgnoreCase(widget.get(PlaywrightTags.WebTagName, ""))) {
                        List<ElementHandle> options = widget.getElementHandle().querySelectorAll("option");
                        List<String> optionValues = new ArrayList<>();

                        for (ElementHandle option : options) {
                            String value = option.getAttribute("value");
                            optionValues.add(value != null ? value : "");
                        }

                        if (!optionValues.isEmpty()) {
                            widgetInfo.put("options", String.join(", ", optionValues));
                        }
                    }
                    // Otherwise, add the text content
                    else {
                        widgetInfo.put("text", widget.get(PlaywrightTags.WebLocatorText).replaceAll("\\s+", " ").trim());
                    }

                    // Then serialize each widget as JSON or custom line format:
                    widgetsContext.add(widgetInfo.entrySet().stream()
                            .map(e -> e.getKey() + ": " + e.getValue())
                            .collect(Collectors.joining(" | "))
                    );

                    // Save them in the INGenious object repository
                    try {
                        dataWriter.addWidgetObject(widget, state.getPage());
                    } catch (Exception e) {
                        addSevereLog("Failed add action objects to the OR" + e.getMessage());
                    }
                }
            }

        } catch (PlaywrightException e) {
            addSevereLog("Failed to collect state interactive elements: " + e.getMessage());
            return Feedback.issue("Trying to obtain state interactive elements information: " + e.getMessage());
        }

        return Feedback.validContext(String.join("\n", widgetsContext));
    }

    private boolean isExternalLink(PlaywrightState state, String href) {
        if (href == null || href.isEmpty()) return false;

        href = href.trim().toLowerCase();

        // Consider these schemes always external
        if (href.startsWith("mailto:") || href.startsWith("tel:") || href.startsWith("javascript:")) return true;

        // Internal anchors or root-relative paths are internal
        if (href.startsWith("/") || href.startsWith("#")) return false;

        // Relative URLs without a scheme or domain are internal
        if (!href.startsWith("http://") && !href.startsWith("https://")) return false;

        // For full URLs, check domain
        String testDomain = extractHostDomain(state.getPage().url());
        return !href.contains(testDomain);
    }

    private String extractHostDomain(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();

            if (host != null && host.startsWith("www.")) {
                host = host.substring(4);
            }

            return host;

        } catch (URISyntaxException e) {
            addSevereLog("Exception extracting the host domain of: " + url);
        }

        return "";
    }

    @Override
    public Feedback executeClickAction(String bddStep, String rawCssSelector) {
        if (this.state == null) return Feedback.issue("No web state-page initialized.");

        addInfoLog("rawCssSelector: " + rawCssSelector);

        String cssSelector = normalizeCssSelector(rawCssSelector);

        if (cssSelector == null || cssSelector.trim().isEmpty()) {
            addSevereLog("ISSUE: Invalid CSS selector: " + rawCssSelector);
            return Feedback.issue("Invalid CSS selector: " + rawCssSelector);
        }

        try {
            addInfoLog("Normalized CSS Selector: " + cssSelector);

            PlaywrightWidget widget = state.getWidgetFromCssSelector(cssSelector);

            if (widget == null) {
                addSevereLog("ISSUE: No matching element found for CSS selector: " + cssSelector);
                return Feedback.issue("No matching element found for CSS selector: " + cssSelector);
            }

            PlaywrightClick clickAction = new PlaywrightClick(widget);
            dataWriter.addActionTestStep(bddStep, state, clickAction, state.getPage());

            clickAction.run(system, state, 0);

            String actionDescription = "Executed Click Action in the widget " + cssSelector;
            saveExecutedAction(actionDescription);

            return Feedback.validContext(actionDescription);
        } catch (Exception e) {
            addSevereLog("Failed to execute action for selector: " + cssSelector + " - " + e.getMessage());
            return Feedback.issue("Executing a click action: " + e.getMessage());
        }
    }

    @Override
    public Feedback executeFillAction(String bddStep, String rawCssSelector, String fillText) {
        if (this.state == null) return Feedback.issue("No web state-page initialized.");

        addInfoLog("rawCssSelector: " + rawCssSelector);

        String cssSelector = normalizeCssSelector(rawCssSelector);

        if (cssSelector == null || cssSelector.trim().isEmpty()) {
            addSevereLog("ISSUE: Invalid CSS selector: " + rawCssSelector);
            return Feedback.issue("Invalid CSS selector: " + rawCssSelector);
        }

        try {
            addInfoLog("Normalized CSS Selector: " + cssSelector);

            PlaywrightWidget widget = state.getWidgetFromCssSelector(cssSelector);

            if (widget == null) {
                addSevereLog("ISSUE: No matching element found for CSS selector: " + cssSelector);
                return Feedback.issue("No matching element found for CSS selector: " + cssSelector);
            }

            PlaywrightFill fillAction = new PlaywrightFill(widget, fillText);
            dataWriter.addActionTestStep(bddStep, state, fillAction, state.getPage());

            fillAction.run(system, state, 0);

            String actionDescription = "Executed Fill Action " + fillText + " in the widget " + cssSelector;
            saveExecutedAction(actionDescription);

            return Feedback.validContext(actionDescription);
        } catch (Exception e) {
            addSevereLog("Failed to execute action for selector: " + cssSelector + " - " + e.getMessage());
            return Feedback.issue("Executing a fill action: " + e.getMessage());
        }
    }

    @Override
    public Feedback executeSelectAction(String bddStep, String rawCssSelector, String optionValue) {
        if (this.state == null) return Feedback.issue("No web state-page initialized.");

        addInfoLog("rawCssSelector: " + rawCssSelector);

        String cssSelector = normalizeCssSelector(rawCssSelector);

        if (cssSelector == null || cssSelector.trim().isEmpty()) {
            addSevereLog("ISSUE: Invalid CSS selector: " + rawCssSelector);
            return Feedback.issue("Invalid CSS selector: " + rawCssSelector);
        }

        try {
            addInfoLog("Normalized CSS Selector: " + cssSelector);

            PlaywrightWidget widget = state.getWidgetFromCssSelector(cssSelector);

            if (widget == null) {
                addSevereLog("ISSUE: No matching element found for CSS selector: " + cssSelector);
                return Feedback.issue("No matching element found for CSS selector: " + cssSelector);
            }

            PlaywrightSelect selectAction = new PlaywrightSelect(widget, optionValue);
            dataWriter.addActionTestStep(bddStep, state, selectAction, state.getPage());

            selectAction.run(system, state, 0);

            String actionDescription = "Select value " + optionValue + " in the widget " + cssSelector;
            saveExecutedAction(actionDescription);

            return Feedback.validContext(actionDescription);
        } catch (Exception e) {
            addSevereLog("Failed to execute select action for selector: " + cssSelector + " - " + e.getMessage());
            return Feedback.issue("Executing a select action: " + e.getMessage());
        }
    }

    private String normalizeCssSelector(String rawSelector) {
        if (rawSelector == null) return null;

        // 1. Unescape common over-escaped characters
        String normalized = rawSelector
                .replaceAll("\\\\/", "/")
                .replaceAll("\\\\:", ":")
                .replaceAll("\\\\'", "'")
                .replaceAll("\\\\\"", "\"")
                .replaceAll("\\\\\\\\", "\\\\"); // double backslashes

        // 2. Trim and basic cleanup
        normalized = normalized.trim();

        // 3. Optional: Basic sanity check (e.g., must start with . or # or tag)
        if (!normalized.matches("^[.#\\[]?.+")) {
            return null;
        }

        return normalized;
    }

    private void saveExecutedAction(String actionDescription) {
        executedAction.add(actionDescription);
    }

    @Override
    public Feedback checkExecutedActions() {
        if(executedAction.isEmpty()) return Feedback.validContext("No executed actions yet!");

        return Feedback.validContext(String.join(", ", executedAction));
    }

    @Override
    public Feedback getStateImage() {
        try {
            byte[] screenshotBytes = state.getScreenshot();
            return Feedback.validContext(Base64.getEncoder().encodeToString(screenshotBytes));
        } catch (Exception e){
            addSevereLog("Failed to obtain the getStateImage: " + e.getMessage());
            return Feedback.validContext(""); // This empty string is used in the MCPAgent switch
        }
    }

    public Feedback getStateVisualText() {
        if (this.state == null) return Feedback.issue("No web state-page initialized.");

        List<String> widgetsContext = new ArrayList<>();

        try {
            state = new PlaywrightState(system);

            List<PlaywrightWidget> stateWidgets = state.getVisibleWidgetsWithText();

            for (PlaywrightWidget widget : stateWidgets) {
                // Prepare the web widget text context to be sent to the AI agent
                Map<String, String> widgetInfo = new LinkedHashMap<>();
                widgetInfo.put("text", widget.get(PlaywrightTags.WebLocatorText).replaceAll("\\s+", " ").trim());

                // Then serialize each widget as JSON or custom line format:
                widgetsContext.add(widgetInfo.entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining(" | "))
                );
            }
        } catch (PlaywrightException e) {
            addSevereLog("Failed to collect visible text of state elements: " + e.getMessage());
            return Feedback.issue("Trying to obtain visible text of state elements: " + e.getMessage());
        }

        return Feedback.validContext(String.join("\n", widgetsContext));
    }

    @Override
    public Feedback addStepAssert(String bddStep, String assertText) {
        if (this.state == null) return Feedback.issue("No web state-page initialized.");

        // Verify that the LLM assertText can be used as locator for assertion
        Locator locator = state.getPage().locator("text=" + assertText);
        if (locator.count() == 0 ) {
            return Feedback.issue("The provided assert text to be used as locator does not match with any GUI web element. " +
                    "Try again with a correct text locator.");
        }
        else if (locator.count() > 1) {
            return Feedback.issue("The provided assert text locator is not unique because matches more than one GUI web element");
        }
        else if (!locator.first().isVisible()) {
            return Feedback.issue("The assert text locator is correct but the GUI web element is not visible. " +
                    "Try again with a correct text locator.");
        }

        // Save the execution steps when we have valid asserts for the BDD instructions
        dataWriter.addAssertTestStep(bddStep, state, assertText);
        dataWriter.saveExecutionSteps();

        return Feedback.validContext("Assertion created successfully!");
    }

    @Override
    public void stopTestExecution() {
        // At the end of the generated sequence, save the generated INGenious testCase
        dataWriter.saveExecutionSteps();
        // Then, close the playwright session
        if (this.system == null) {
            addInfoLog("stopTestExecution called without an active Playwright session.");
            return;
        }

        try {
            this.system.stop();
        } catch (Exception e) {
            addSevereLog("Failed to stop Playwright session: " + e.getMessage());
        } finally {
            this.system = null;
            this.state = null;
        }
    }

    private void addInfoLog(String msg){
        java.util.logging.Logger.getLogger(PlaywrightMcpDriver.class.getName()).log(
                java.util.logging.Level.INFO,
                msg
        );
    }

    private void addSevereLog(String msg){
        java.util.logging.Logger.getLogger(PlaywrightMcpDriver.class.getName()).log(
                java.util.logging.Level.SEVERE,
                msg
        );
    }

}
