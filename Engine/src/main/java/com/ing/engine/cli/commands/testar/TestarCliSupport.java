package com.ing.engine.cli.commands.testar;

import com.ing.engine.cli.INGeniousCLI;
import com.ing.engine.cli.commands.testar.daemon.TestarCliResponse;
import com.ing.ingenious.api.contract.testar.TestarResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public final class TestarCliSupport {

    private TestarCliSupport() {
    }

    public static int printResult(INGeniousCLI cli, TestarResult result) {
        if (result == null) {
            cli.printError("No TESTAR backend result available.");
            return 1;
        }

        if (cli.isJsonOutput()) {
            System.out.println(toJson(result));
        } else {
            String status = result.getStatus() != null ? result.getStatus() : "unknown";
            String message = result.getMessage() != null ? result.getMessage() : "";
            String payload = result.getPayload() != null ? result.getPayload() : "";

            System.out.println("status: " + status);
            if (!message.isEmpty()) {
                System.out.println("message: " + message);
            }
            if (!payload.isEmpty() && !payload.equals(message)) {
                System.out.println("payload: " + payload);
            }
        }

        return result.isSuccess() ? 0 : 1;
    }

    public static int printDaemonResponse(INGeniousCLI cli, TestarCliResponse response) {
        if (response == null) {
            cli.printError("No TESTAR daemon response available.");
            return 1;
        }

        Map<String, String> values = parseResponseValues(response);
        TestarResult result = toResult(values, response.getExitCode());

        if (cli.isJsonOutput()) {
            System.out.println(toJson(result));
            return response.getExitCode();
        }

        String status = result.getStatus() != null ? result.getStatus() : "unknown";
        String message = result.getMessage() != null ? result.getMessage() : "";
        String payload = result.getPayload() != null ? result.getPayload() : "";
        Map<String, String> payloadEntries = parsePayloadEntries(payload);

        System.out.println("status: " + status);
        if (!message.isEmpty()) {
            System.out.println("message: " + message);
        }
        if (!payload.isEmpty() && !payload.equals(message) && payloadEntries.isEmpty()) {
            System.out.println("payload: " + payload);
        }
        for (Entry<String, String> entry : payloadEntries.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }

        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = entry.getKey();
            if ("success".equals(key) || "status".equals(key) || "message".equals(key) || "payload".equals(key)) {
                continue;
            }
            System.out.println(key + ": " + entry.getValue());
        }

        return response.getExitCode();
    }

    public static TestarResult toResult(TestarCliResponse response) {
        if (response == null) {
            return TestarResult.failure("error", "No TESTAR daemon response available.");
        }

        return toResult(parseResponseValues(response), response.getExitCode());
    }

    private static TestarResult toResult(Map<String, String> values, int exitCode) {
        boolean success = exitCode == 0;
        String status = values.getOrDefault("status", success ? "ok" : "error");
        String message = values.getOrDefault("message", "");
        String payload = values.get("payload");
        return new TestarResult(success, status, message, payload);
    }

    private static Map<String, String> parseResponseValues(TestarCliResponse response) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : response.getLines()) {
            int separatorIndex = line.indexOf('=');
            if (separatorIndex > 0) {
                String key = line.substring(0, separatorIndex);
                String value = line.substring(separatorIndex + 1)
                        .replace("\\r", "\r")
                        .replace("\\n", "\n");
                values.put(key, value);
            }
        }
        return values;
    }

    private static Map<String, String> parsePayloadEntries(String payload) {
        Map<String, String> entries = new LinkedHashMap<>();
        if (payload == null || payload.isEmpty()) {
            return entries;
        }

        String[] lines = payload.split("\\r?\\n");
        for (String line : lines) {
            int separatorIndex = line.indexOf(':');
            if (separatorIndex <= 0) {
                return new LinkedHashMap<>();
            }

            String key = line.substring(0, separatorIndex).trim();
            String value = line.substring(separatorIndex + 1).trim();
            if (key.isEmpty()) {
                return new LinkedHashMap<>();
            }
            entries.put(key, value);
        }
        return entries;
    }

    private static String toJson(TestarResult result) {
        return "{"
                + "\"success\":" + result.isSuccess() + ","
                + "\"status\":" + toJsonValue(result.getStatus()) + ","
                + "\"message\":" + toJsonValue(result.getMessage()) + ","
                + "\"payload\":" + toJsonValue(result.getPayload())
                + "}";
    }

    private static String toJsonValue(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
    }
}
