package com.ing.ide.main.testar.mcp;

/**
 * Exception thrown when an LLM provider encounters an error during
 * communication.
 * 
 * <p>
 * This exception wraps various error conditions that may occur during
 * LLM API interactions, including:
 * </p>
 * <ul>
 * <li>Network connectivity issues</li>
 * <li>Authentication failures (invalid API keys)</li>
 * <li>Rate limiting (HTTP 429)</li>
 * <li>Server errors (HTTP 5xx)</li>
 * <li>JSON parsing errors</li>
 * </ul>
 * 
 * @author TFG-MCP-TESTAR Team
 * @version 1.0
 * @since 2024
 */
public class LlmProviderException extends RuntimeException {

    private final int httpStatusCode;
    private final boolean isRateLimited;
    private final long retryAfterMs;

    /**
     * Constructs a new LlmProviderException with only a message.
     * 
     * @param message the detail message
     */
    public LlmProviderException(String message) {
        super(message);
        this.httpStatusCode = -1;
        this.isRateLimited = false;
        this.retryAfterMs = 0;
    }

    /**
     * Constructs a new LlmProviderException with a message and cause.
     * 
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public LlmProviderException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatusCode = -1;
        this.isRateLimited = false;
        this.retryAfterMs = 0;
    }

    /**
     * Constructs a new LlmProviderException for HTTP errors.
     * 
     * @param message        the detail message
     * @param httpStatusCode the HTTP status code
     * @param isRateLimited  whether this is a rate limiting error (429)
     * @param retryAfterMs   suggested retry delay in milliseconds
     */
    public LlmProviderException(String message, int httpStatusCode,
            boolean isRateLimited, long retryAfterMs) {
        super(message);
        this.httpStatusCode = httpStatusCode;
        this.isRateLimited = isRateLimited;
        this.retryAfterMs = retryAfterMs;
    }

    /**
     * @return the HTTP status code, or -1 if not an HTTP error
     */
    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    /**
     * @return true if this error is due to rate limiting (HTTP 429)
     */
    public boolean isRateLimited() {
        return isRateLimited;
    }

    /**
     * @return suggested retry delay in milliseconds, or 0 if not applicable
     */
    public long getRetryAfterMs() {
        return retryAfterMs;
    }
}
