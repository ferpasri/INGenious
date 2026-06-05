package com.ing.ide.main.testar.service.state;

import com.ing.ide.main.testar.mcp.Feedback;
import com.ing.ide.main.testar.playwright.system.PlaywrightState;
import com.ing.ide.main.testar.playwright.system.PlaywrightTags;
import com.ing.ide.main.testar.playwright.system.PlaywrightWidget;
import com.ing.ide.main.testar.service.SessionContext;
import com.ing.ide.main.testar.service.action.PlaywrightActionService;
import com.ing.ide.main.testar.service.persistence.PersistenceService;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.PlaywrightException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class PlaywrightStateService implements StateService {

    private final PersistenceService persistenceService;

    public PlaywrightStateService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Override
    public Feedback getStateInteractiveWidgets(SessionContext context) {
        if (context.getState() == null) {
            return Feedback.issue(Feedback.Code.STATE_NOT_INITIALIZED, "No web state-page initialized.");
        }

        List<String> widgetsContext = new ArrayList<>();

        try {
            context.setState(new PlaywrightState(context.getSystem()));

            List<PlaywrightWidget> stateWidgets = context.getState().getInteractiveWidgets();

            for (PlaywrightWidget widget : stateWidgets) {

                // For widgets with CSS locators
                if (!widget.get(PlaywrightTags.WebLocatorCSS, "").isEmpty()
                        && !isExternalLink(context.getState(), widget.get(PlaywrightTags.WebHref, ""))) {

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
                        persistenceService.persistWidgetObject(widget, context.getState().getPage());
                    } catch (Exception e) {
                        addSevereLog("Failed add action objects to the OR" + e.getMessage());
                    }
                }
            }

        } catch (PlaywrightException e) {
            addSevereLog("Failed to collect state interactive elements: " + e.getMessage());
            return Feedback.issue(Feedback.Code.STATE_WIDGETS_COLLECTION_FAILED, "Trying to obtain state interactive elements information: " + e.getMessage());
        }

        return Feedback.validContext(String.join("\n", widgetsContext));
    }

    @Override
    public Feedback getStateImage(SessionContext context) {
        if (context.getState() == null) {
            return Feedback.issue(Feedback.Code.STATE_NOT_INITIALIZED, "No web state-page initialized.");
        }

        try {
            byte[] screenshotBytes = context.getState().getScreenshot();
            return Feedback.validContext(Base64.getEncoder().encodeToString(screenshotBytes));
        } catch (Exception e) {
            addSevereLog("Failed to obtain the getStateImage: " + e.getMessage());
            return Feedback.validContext("");
        }
    }

    @Override
    public Feedback getStateVisualText(SessionContext context) {
        if (context.getState() == null) {
            return Feedback.issue(Feedback.Code.STATE_NOT_INITIALIZED, "No web state-page initialized.");
        }

        List<String> widgetsContext = new ArrayList<>();

        try {
            context.setState(new PlaywrightState(context.getSystem()));

            List<PlaywrightWidget> stateWidgets = context.getState().getVisibleWidgetsWithText();

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
            return Feedback.issue(Feedback.Code.STATE_VISUAL_TEXT_COLLECTION_FAILED, "Trying to obtain visible text of state elements: " + e.getMessage());
        }

        return Feedback.validContext(String.join("\n", widgetsContext));
    }

    private boolean isExternalLink(PlaywrightState state, String href) {
        if (href == null || href.isEmpty()) {
            return false;
        }

        href = href.trim().toLowerCase();

        // Consider these schemes always external
        if (href.startsWith("mailto:") || href.startsWith("tel:") || href.startsWith("javascript:")) {
            return true;
        }

        // Internal anchors or root-relative paths are internal
        if (href.startsWith("/") || href.startsWith("#")) {
            return false;
        }

        // Relative URLs without a scheme or domain are internal
        if (!href.startsWith("http://") && !href.startsWith("https://")) {
            return false;
        }

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

    private void addSevereLog(String msg) {
        Logger.getLogger(PlaywrightActionService.class.getName()).log(Level.SEVERE, msg);
    }
}
