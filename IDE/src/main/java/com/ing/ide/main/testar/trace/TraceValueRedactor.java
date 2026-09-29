package com.ing.ide.main.testar.trace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class TraceValueRedactor {
    private static final String REDACTED = "[REDACTED]";
    private static final Pattern BEARER_TOKEN = Pattern.compile(
        "(?i)(Bearer\\s+)[A-Za-z0-9._~+/-]+=*"
    );

    private TraceValueRedactor() {}

    public static Map<String, Object> redactMap(Map<String, ?> values) {
        Map<String, Object> redacted = new LinkedHashMap<>();
        if (values == null) {
            return redacted;
        }

        values.forEach((key, value) -> redacted.put(key, redactValue(key, value)));
        return redacted;
    }

    public static String redactString(String value) {
        if (value == null) {
            return "";
        }
        String redacted = BEARER_TOKEN.matcher(value).replaceAll("$1" + REDACTED);
        return Pattern
            .compile(
                "(?i)((?:password|api[_-]?key|access[_-]?token|refresh[_-]?token)\\s*[:=]\\s*)[^\\s,;]+"
            )
            .matcher(redacted)
            .replaceAll("$1" + REDACTED);
    }

    public static String redactTestDataValue(String column, String value) {
        return isSensitiveKey(column) ? REDACTED : redactString(value);
    }

    private static Object redactValue(String key, Object value) {
        if (isSensitiveKey(key)) {
            return REDACTED;
        }
        if (value instanceof Map<?, ?>) {
            Map<String, Object> nested = new LinkedHashMap<>();
            ((Map<?, ?>) value).forEach(
                    (nestedKey, nestedValue) -> {
                        String name = String.valueOf(nestedKey);
                        nested.put(name, redactValue(name, nestedValue));
                    }
                );
            return nested;
        }
        if (value instanceof List<?>) {
            List<Object> nested = new ArrayList<>();
            for (Object item : (List<?>) value) {
                nested.add(redactValue(key, item));
            }
            return nested;
        }
        if (value instanceof String) {
            return redactString((String) value);
        }
        return value;
    }

    private static boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return (
            normalized.contains("password") ||
            normalized.contains("secret") ||
            normalized.contains("apikey") ||
            normalized.contains("accesstoken") ||
            normalized.contains("refreshtoken") ||
            normalized.contains("authorization") ||
            normalized.equals("token")
        );
    }
}
