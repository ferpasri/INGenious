package com.ing.ide.main.testar.codex;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.file.Path;

public class CodexRunResult {

    private final boolean success;
    private final String status;
    private final String threadId;
    private final String finalResponse;
    private final String errorMessage;
    private final Path runDirectory;
    private final Usage usage;

    public CodexRunResult(boolean success,
                          String status,
                          String threadId,
                          String finalResponse,
                          String errorMessage,
                          Path runDirectory,
                          Usage usage) {
        this.success = success;
        this.status = status != null ? status : "";
        this.threadId = threadId != null ? threadId : "";
        this.finalResponse = finalResponse != null ? finalResponse : "";
        this.errorMessage = errorMessage != null ? errorMessage : "";
        this.runDirectory = runDirectory;
        this.usage = usage;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getStatus() {
        return status;
    }

    public String getThreadId() {
        return threadId;
    }

    public String getFinalResponse() {
        return finalResponse;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Path getRunDirectory() {
        return runDirectory;
    }

    public Usage getUsage() {
        return usage;
    }

    public String toNotificationMessage() {
        if (!success) {
            return errorMessage.isBlank() ? "Codex agent execution failed." : errorMessage;
        }

        StringBuilder message = new StringBuilder("Codex agent run completed.");
        if (usage != null) {
            message.append(" Tokens: in=").append(usage.inputTokens)
                    .append(", out=").append(usage.outputTokens)
                    .append(", reasoning=").append(usage.reasoningOutputTokens);
        }
        return message.toString();
    }

    public static class Usage {

        @JsonProperty("input_tokens")
        public int inputTokens;

        @JsonProperty("cached_input_tokens")
        public int cachedInputTokens;

        @JsonProperty("output_tokens")
        public int outputTokens;

        @JsonProperty("reasoning_output_tokens")
        public int reasoningOutputTokens;

        public Usage() { }
    }

}
