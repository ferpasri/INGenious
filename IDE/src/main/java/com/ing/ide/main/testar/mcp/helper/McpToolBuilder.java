package com.ing.ide.main.testar.mcp.helper;

import com.ing.ide.main.testar.mcp.McpInterface;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds OpenAI Chat Completions "tools" definitions from the annotated {@link McpInterface} using reflection.
 * <p>
 * This utility scans the given API interface for methods annotated with {@link McpMethod}
 * and parameters annotated with {@link McpParam}, and produces a List of Maps
 * ready to serialize as the request body’s {@code "tools"} array.
 */
public final class McpToolBuilder {

    private McpToolBuilder() {}

        /*
        "tools": [
            {
                "type": "function",
                "function": {
                    "name": "getState",
                    "description": "Get a list of current interactive GUI web elements with CSS selector, visible text, tag type, and accessibility attributes.",
                    "parameters": {
                        "type": "object",
                        "properties": {},
                        // No inputs -> nothing is required
                        "additionalProperties": false
                    }
                }
            },
            {
                "type": "function",
                "function": {
                    "name": "executeClickAction",
                    "description": "Use a CSS selector to click an element.",
                    "parameters": {
                        "type": "object",
                        "properties": {
                            "bddStep": {
                                "type": "string",
                                "description": "The BDD step text associated with this action. Do not modify."
                            },
                            "cssSelector": {
                                "type": "string",
                                "description": "The CSS selector of the clickable element."
                            }
                        },
                        "required": ["bddStep", "cssSelector"],
                        "additionalProperties": false
                    }
                }
            }
        ]
    */

    public static List<Map<String,Object>> from(Class<?> apiInterface) {
        List<Map<String,Object>> functionsMap = new ArrayList<>();
        for (Method m : apiInterface.getMethods()) {
            McpMethod mcpMethod = m.getAnnotation(McpMethod.class);
            if (mcpMethod == null) continue;

            String name = mcpMethod.name().isEmpty() ? m.getName() : mcpMethod.name();
            Map<String,Object> props = new LinkedHashMap<>();
            List<String> required = new ArrayList<>();

            for (Parameter p : m.getParameters()) {
                McpParam mcpParam = p.getAnnotation(McpParam.class);
                if (mcpParam == null) {
                    java.util.logging.Logger.getLogger(McpToolBuilder.class.getName()).log(
                            java.util.logging.Level.SEVERE,
                            "Parameter missing mcpParam on " + m
                    );
                    continue;
                }

                Map<String,Object> schema = new LinkedHashMap<>();
                schema.put("type", "string");
                if (!mcpParam.description().isEmpty()) schema.put("description", mcpParam.description());
                props.put(mcpParam.name(), schema);
                if (mcpParam.required()) required.add(mcpParam.name());
            }

            Map<String,Object> params = new LinkedHashMap<>();
            params.put("type", "object");
            params.put("properties", props);
            params.put("additionalProperties", false);
            params.put("required", required);

            Map<String,Object> tool = new LinkedHashMap<>();
            tool.put("type", "function");
            tool.put("name", name);
            tool.put("description", mcpMethod.description());
            tool.put("parameters", params);
            tool.put("strict", Boolean.TRUE);

            functionsMap.add(tool);
        }
        return functionsMap;
    }

}
