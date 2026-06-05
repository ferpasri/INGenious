package com.ing.ide.main.testar.mcp.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ing.ide.main.testar.mcp.Feedback;

/**
 * Convert raw MCP feedback into stable invalid-action metrics categories.
 */
public final class LlmInvalidActionClassifier {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private LlmInvalidActionClassifier() { }

    public static InvalidActionClassification classify(String toolName, String argumentsJson, Feedback feedback) {
        if (feedback != null && feedback.getCode() != null && feedback.getCode() != Feedback.Code.NONE) {
            return fromFeedbackCode(argumentsJson, feedback.getCode(), feedback.toString());
        }
        return classify(toolName, argumentsJson, feedback != null ? feedback.toString() : "");
    }

    public static InvalidActionClassification classify(String toolName, String argumentsJson, String feedbackMessage) {
        String normalizedMessage = feedbackMessage == null ? "" : feedbackMessage.trim();
        String normalizedLower = normalizedMessage.toLowerCase();
        String bddStep = extractArgument(argumentsJson, "bddStep");
        String selector = extractArgument(argumentsJson, "cssSelector");
        String value = extractArgument(argumentsJson, "fillText");
        if (value.isEmpty()) {
            value = extractArgument(argumentsJson, "optionValue");
        }
        if (value.isEmpty()) {
            value = extractArgument(argumentsJson, "assertText");
        }
        if (value.isEmpty()) {
            value = extractArgument(argumentsJson, "url");
        }

        if (normalizedLower.contains("invalid css selector")) {
            return new InvalidActionClassification(
                    bddStep,
                    selector,
                    value,
                    "css_selector_invalid",
                    "Invalid or malformed CSS selector."
            );
        }

        if (normalizedLower.contains("no matching element found for css selector")) {
            return new InvalidActionClassification(
                    bddStep,
                    selector,
                    value,
                    "css_selector_no_match",
                    "Selector did not match any element. Likely selector hallucination or stale GUI state."
            );
        }

        if (normalizedLower.contains("does not seem to match with original bdd instructions")) {
            return new InvalidActionClassification(
                    bddStep,
                    selector,
                    value,
                    "bdd_step_not_in_original_scenario",
                    "BDD step does not match the original scenario."
            );
        }

        if (normalizedLower.contains("is not the current or a new step")) {
            return new InvalidActionClassification(
                    bddStep,
                    selector,
                    value,
                    "bdd_step_stale_reuse",
                    "BDD step reuses an old step instead of the latest or a new one."
            );
        }

        if (normalizedLower.contains("assert text locator is not unique")) {
            return new InvalidActionClassification(
                    bddStep,
                    selector,
                    value,
                    "assert_text_not_unique",
                    "Assert text matched more than one visible element."
            );
        }

        if (normalizedLower.contains("assert text to be used as locator does not match")) {
            return new InvalidActionClassification(
                    bddStep,
                    selector,
                    value,
                    "assert_text_not_found",
                    "Assert text did not match any visible element."
            );
        }

        if (normalizedLower.contains("assert text locator is correct but the gui web element is not visible")) {
            return new InvalidActionClassification(
                    bddStep,
                    selector,
                    value,
                    "assert_text_not_visible",
                    "Assert text matched an element that is not visible."
            );
        }

        if (normalizedLower.contains("no web state-page initialized")) {
            return new InvalidActionClassification(
                    bddStep,
                    selector,
                    value,
                    "state_not_initialized",
                    "Action was attempted before a valid web state was available."
            );
        }

        if (normalizedLower.contains("cannot navigate back")) {
            return new InvalidActionClassification(
                    bddStep,
                    selector,
                    value,
                    "navigation_back_unavailable",
                    "Navigation back was attempted without a previous page."
            );
        }

        if (normalizedLower.contains("no web url available")) {
            return new InvalidActionClassification(
                    bddStep,
                    selector,
                    value,
                    "url_not_available",
                    "Current URL was requested but no page URL was available."
            );
        }

        if (normalizedLower.contains("trying to obtain state interactive elements information")) {
            return new InvalidActionClassification(
                    bddStep,
                    selector,
                    value,
                    "state_widgets_collection_failed",
                    "Interactive widget state collection failed."
            );
        }

        if (normalizedLower.contains("trying to obtain visible text of state elements")) {
            return new InvalidActionClassification(
                    bddStep,
                    selector,
                    value,
                    "state_visual_text_collection_failed",
                    "Visible text state collection failed."
            );
        }

        if (normalizedLower.contains("loading the web url")) {
            return new InvalidActionClassification(
                    bddStep,
                    selector,
                    value,
                    "load_url_failed",
                    "Loading the requested URL failed."
            );
        }

        return new InvalidActionClassification(
                bddStep,
                selector,
                value,
                "other_invalid_action",
                "Other invalid action based on MCP feedback."
        );
    }

    private static InvalidActionClassification fromFeedbackCode(String argumentsJson,
                                                                Feedback.Code code,
                                                                String feedbackMessage) {
        String bddStep = extractArgument(argumentsJson, "bddStep");
        String selector = extractArgument(argumentsJson, "cssSelector");
        String value = extractArgument(argumentsJson, "fillText");
        if (value.isEmpty()) {
            value = extractArgument(argumentsJson, "optionValue");
        }
        if (value.isEmpty()) {
            value = extractArgument(argumentsJson, "assertText");
        }
        if (value.isEmpty()) {
            value = extractArgument(argumentsJson, "url");
        }

        switch (code) {
            case BDD_STEP_EMPTY_OR_INVALID:
                return new InvalidActionClassification(bddStep, selector, value, "bdd_step_empty_or_invalid", "BDD step is empty or invalid.");
            case BDD_STEP_NOT_IN_ORIGINAL_SCENARIO:
                return new InvalidActionClassification(bddStep, selector, value, "bdd_step_not_in_original_scenario", "BDD step does not match the original scenario.");
            case BDD_STEP_STALE_REUSE:
                return new InvalidActionClassification(bddStep, selector, value, "bdd_step_stale_reuse", "BDD step reuses an old step instead of the latest or a new one.");
            case STATE_NOT_INITIALIZED:
                return new InvalidActionClassification(bddStep, selector, value, "state_not_initialized", "Action was attempted before a valid web state was available.");
            case CSS_SELECTOR_INVALID:
                return new InvalidActionClassification(bddStep, selector, value, "css_selector_invalid", "Invalid or malformed CSS selector.");
            case CSS_SELECTOR_NO_MATCH:
                return new InvalidActionClassification(bddStep, selector, value, "css_selector_no_match", "Selector did not match any element. Likely selector hallucination or stale GUI state.");
            case ASSERT_TEXT_NOT_FOUND:
                return new InvalidActionClassification(bddStep, selector, value, "assert_text_not_found", "Assert text did not match any visible element.");
            case ASSERT_TEXT_NOT_UNIQUE:
                return new InvalidActionClassification(bddStep, selector, value, "assert_text_not_unique", "Assert text matched more than one visible element.");
            case ASSERT_TEXT_NOT_VISIBLE:
                return new InvalidActionClassification(bddStep, selector, value, "assert_text_not_visible", "Assert text matched an element that is not visible.");
            case LOAD_URL_FAILED:
                return new InvalidActionClassification(bddStep, selector, value, "load_url_failed", "Loading the requested URL failed.");
            case URL_NOT_AVAILABLE:
                return new InvalidActionClassification(bddStep, selector, value, "url_not_available", "Current URL was requested but no page URL was available.");
            case NAVIGATION_BACK_UNAVAILABLE:
                return new InvalidActionClassification(bddStep, selector, value, "navigation_back_unavailable", "Navigation back was attempted without a previous page.");
            case STATE_WIDGETS_COLLECTION_FAILED:
                return new InvalidActionClassification(bddStep, selector, value, "state_widgets_collection_failed", "Interactive widget state collection failed.");
            case STATE_VISUAL_TEXT_COLLECTION_FAILED:
                return new InvalidActionClassification(bddStep, selector, value, "state_visual_text_collection_failed", "Visible text state collection failed.");
            case CLICK_ACTION_EXECUTION_FAILED:
                return new InvalidActionClassification(bddStep, selector, value, "click_action_execution_failed", "Click action execution failed.");
            case FILL_ACTION_EXECUTION_FAILED:
                return new InvalidActionClassification(bddStep, selector, value, "fill_action_execution_failed", "Fill action execution failed.");
            case SELECT_ACTION_EXECUTION_FAILED:
                return new InvalidActionClassification(bddStep, selector, value, "select_action_execution_failed", "Select action execution failed.");
            case OTHER_INVALID_ACTION:
            case NONE:
            default:
                return classify("", argumentsJson, feedbackMessage);
        }
    }

    private static String extractArgument(String argumentsJson, String fieldName) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return "";
        }

        try {
            JsonNode root = JSON_MAPPER.readTree(argumentsJson);
            JsonNode node = root.get(fieldName);
            return node != null && !node.isNull() ? node.asText("") : "";
        } catch (Exception exception) {
            return "";
        }
    }

    public static final class InvalidActionClassification {

        private final String bddStep;
        private final String selector;
        private final String value;
        private final String reasonCode;
        private final String reasonSummary;

        public InvalidActionClassification(String bddStep,
                                           String selector,
                                           String value,
                                           String reasonCode,
                                           String reasonSummary) {
            this.bddStep = bddStep;
            this.selector = selector;
            this.value = value;
            this.reasonCode = reasonCode;
            this.reasonSummary = reasonSummary;
        }

        public String getBddStep() {
            return bddStep;
        }

        public String getSelector() {
            return selector;
        }

        public String getValue() {
            return value;
        }

        public String getReasonCode() {
            return reasonCode;
        }

        public String getReasonSummary() {
            return reasonSummary;
        }
    }
}
