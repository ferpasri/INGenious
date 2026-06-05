package com.ing.ide.main.testar.mcp.metrics;

/**
 * One invalid MCP tool execution recorded during a Studio TESTAR run.
 */
public class LlmInvalidActionDetail {

    private final int index;
    private final String toolName;
    private final String bddStep;
    private final String selector;
    private final String value;
    private final String reasonCode;
    private final String reasonSummary;
    private final String feedbackMessage;

    public LlmInvalidActionDetail(int index,
                                  String toolName,
                                  String bddStep,
                                  String selector,
                                  String value,
                                  String reasonCode,
                                  String reasonSummary,
                                  String feedbackMessage) {
        this.index = index;
        this.toolName = toolName != null ? toolName : "";
        this.bddStep = bddStep != null ? bddStep : "";
        this.selector = selector != null ? selector : "";
        this.value = value != null ? value : "";
        this.reasonCode = reasonCode != null ? reasonCode : "";
        this.reasonSummary = reasonSummary != null ? reasonSummary : "";
        this.feedbackMessage = feedbackMessage != null ? feedbackMessage : "";
    }

    public int getIndex() {
        return index;
    }

    public String getToolName() {
        return toolName;
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

    public String getFeedbackMessage() {
        return feedbackMessage;
    }
}
