package com.ing.ide.main.testar.mcp.provider;

/**
 * Exception type used by the multi-LLM provider layer.
 *
 * <p>
 * This exception keeps transport-neutral error information so the MCP
 * orchestration loop can make retry and reporting decisions without knowing
 * provider-specific HTTP details.
 * </p>
 */
public class LlmProviderException extends Exception {

    private final int statusCode;
    private final boolean retryable;
    private final long retryAfterMillis;

    public LlmProviderException(String message) {
        this(message, null, 0, false, 0L);
    }

    public LlmProviderException(String message, Throwable cause) {
        this(message, cause, 0, false, 0L);
    }

    public LlmProviderException(String message, int statusCode, boolean retryable, long retryAfterMillis) {
        this(message, null, statusCode, retryable, retryAfterMillis);
    }

    public LlmProviderException(String message,
                                Throwable cause,
                                int statusCode,
                                boolean retryable,
                                long retryAfterMillis) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryable = retryable;
        this.retryAfterMillis = retryAfterMillis;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public long getRetryAfterMillis() {
        return retryAfterMillis;
    }

}
