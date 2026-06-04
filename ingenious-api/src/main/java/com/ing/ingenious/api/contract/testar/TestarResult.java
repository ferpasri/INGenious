package com.ing.ingenious.api.contract.testar;

public class TestarResult {

    private final boolean success;
    private final String status;
    private final String message;
    private final String payload;

    public TestarResult(boolean success, String status, String message, String payload) {
        this.success = success;
        this.status = status;
        this.message = message;
        this.payload = payload;
    }

    public static TestarResult success(String status, String message, String payload) {
        return new TestarResult(true, status, message, payload);
    }

    public static TestarResult failure(String status, String message) {
        return new TestarResult(false, status, message, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public String getPayload() {
        return payload;
    }
}
