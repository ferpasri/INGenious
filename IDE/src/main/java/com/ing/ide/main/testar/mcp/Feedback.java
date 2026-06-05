package com.ing.ide.main.testar.mcp;

/**
 * Standardize agent MCP feedback.
 */
public final class Feedback {

    public enum Code {
        NONE,
        OTHER_INVALID_ACTION,
        BDD_STEP_EMPTY_OR_INVALID,
        BDD_STEP_NOT_IN_ORIGINAL_SCENARIO,
        BDD_STEP_STALE_REUSE,
        STATE_NOT_INITIALIZED,
        CSS_SELECTOR_INVALID,
        CSS_SELECTOR_NO_MATCH,
        CLICK_ACTION_EXECUTION_FAILED,
        FILL_ACTION_EXECUTION_FAILED,
        SELECT_ACTION_EXECUTION_FAILED,
        ASSERT_TEXT_NOT_FOUND,
        ASSERT_TEXT_NOT_UNIQUE,
        ASSERT_TEXT_NOT_VISIBLE,
        LOAD_URL_FAILED,
        URL_NOT_AVAILABLE,
        NAVIGATION_BACK_UNAVAILABLE,
        STATE_WIDGETS_COLLECTION_FAILED,
        STATE_VISUAL_TEXT_COLLECTION_FAILED
    }

    private final Code code;
    private final String message;
    private final boolean issue;

    private Feedback(Code code, String message, boolean issue) {
        this.code = code != null ? code : Code.NONE;
        this.message = message;
        this.issue = issue;
    }

    public static Feedback issue(String message) {
        return issue(Code.OTHER_INVALID_ACTION, message);
    }

    public static Feedback issue(Code code, String message) {
        String trimmed = message == null ? "" : message.trim();

        if (trimmed.isEmpty()) {
            return new Feedback(code, "ISSUE: Unspecified issue.", true);
        }

        return new Feedback(code, "ISSUE: " + trimmed, true);
    }

    public static Feedback validContext(String message) {
        return new Feedback(Code.NONE, message, false);
    }

    public Code getCode() {
        return code;
    }

    public boolean isIssue() {
        return issue;
    }

    @Override
    public String toString() {
        return message;
    }

}
