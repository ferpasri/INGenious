package com.ing.ide.main.testar.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ing.datalib.component.Project;
import com.ing.ide.main.testar.mcp.helper.McpNames;
import com.ing.ide.main.testar.mcp.helper.McpToolBuilder;
import com.ing.ide.main.testar.mcp.helper.McpToolExecutor;
import com.ing.ide.main.testar.mcp.provider.LlmProvider;
import com.ing.ide.main.testar.mcp.provider.LlmProviderException;
import com.ing.ide.main.testar.mcp.provider.LlmProviderFactory;
import com.ing.ide.main.testar.mcp.provider.LlmProviderResponse;
import com.ing.ide.main.testar.mcp.provider.LlmToolCall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LlmMcpAgent {

    private static final Logger LOGGER = Logger.getLogger(LlmMcpAgent.class.getName());

    private final ObjectMapper mapper = new ObjectMapper();

    private final int maxActions;
    private final String bddInstructions;
    private final LlmProvider provider;
    private final McpInterface mcpInterface;

    public LlmMcpAgent(Project project, McpAgentSettings settings) {
        this.maxActions = settings.maxActions != null ? settings.maxActions : 10;
        this.bddInstructions = settings.bddInstructions != null ? settings.bddInstructions : "";

        PlaywrightMcpDriver mcpDriver = new PlaywrightMcpDriver(project, settings.bddScenarioName);
        this.mcpInterface = new BddMcpValidator(mcpDriver, new BddStepTracker(bddInstructions));
        this.provider = LlmProviderFactory.create(settings);
    }

    public String runLLMAgent() {
        final List<Map<String, Object>> input = defineInput();
        final List<Map<String, Object>> tools = McpToolBuilder.from(McpInterface.class);
        final McpToolExecutor<McpInterface> executor = McpToolExecutor.of(McpInterface.class, mcpInterface, mapper);

        provider.registerTools(tools);

        int step = 0;
        boolean visionRequest = false;

        while (step < maxActions) {
            try {
                LlmProviderResponse response = provider.executePrompt(buildInstructions(), input, visionRequest);
                logTokenUsage(provider.getLastTokenUsage());

                if (!response.getAssistantItems().isEmpty()) {
                    input.addAll(response.getAssistantItems());
                }

                List<LlmToolCall> toolCalls = response.getToolCalls();
                if (toolCalls == null || toolCalls.isEmpty()) {
                    String feedback = "ISSUE: No tool was selected. Please review the last results.";
                    addInfoLog(feedback);
                    if (!response.getOutputText().isBlank()) {
                        addInfoLog("MODEL OUTPUT: " + response.getOutputText());
                    }
                    input.add(createUserTextInput("Reminder: choose a valid tool to proceed."));
                    continue;
                }

                List<Map<String, Object>> toolCallResultItems = new ArrayList<>();
                List<Map<String, Object>> userInputItems = new ArrayList<>();

                for (LlmToolCall toolCall : toolCalls) {
                    String toolName = toolCall.getToolName();
                    String argumentsJson = toolCall.getArgumentsJson();
                    String callId = toolCall.getCallId();

                    addInfoLog("DEBUG toolName: " + toolName);
                    addInfoLog("DEBUG argumentsJson: " + argumentsJson);

                    Object resultObj = executor.execute(toolName, argumentsJson);
                    String result = resultObj == null ? "null" : resultObj.toString();

                    if (McpNames.of(McpInterface::stopTestExecution).equals(toolName)) {
                        addInfoLog("LLM agent decided to stop the test execution");
                        return "LLM agent decided to stop the test execution";
                    }

                    boolean requireStateImage = McpNames.of(McpInterface::getStateImage).equals(toolName);
                    String toolContent = requireStateImage ? "screenshot_ready" : result;
                    toolCallResultItems.add(Map.of(
                            "type", "function_call_output",
                            "call_id", callId,
                            "output", toolContent
                    ));

                    if (requireStateImage && !result.isEmpty() && provider.supportsVision()) {
                        addInfoLog("VISION REQUEST: attaching state image");
                        attachStateImage(userInputItems, result);
                        visionRequest = true;
                    } else if (requireStateImage && !result.isEmpty()) {
                        addInfoLog("VISION REQUEST: is omitted for this model or by the user");
                        userInputItems.add(createUserTextInput("Screenshot captured (omitted for this model)."));
                    } else {
                        addInfoLog("DEBUG result: " + result);
                    }
                }

                input.addAll(toolCallResultItems);
                input.addAll(userInputItems);
                step++;
            } catch (LlmProviderException exception) {
                if (exception.isRetryable()) {
                    long waitTime = exception.getRetryAfterMillis() > 0 ? exception.getRetryAfterMillis() : 10000L;
                    addInfoLog("LLM provider rate limited... wait " + (waitTime / 1000) + " seconds...");
                    sleep(waitTime);
                    continue;
                }

                addSevereLog("LLM step failed: " + exception.getMessage());
                return "Stop execution due to LLM call fail: " + exception.getMessage();
            } catch (Exception exception) {
                addSevereLog("LLM step failed: " + exception.getMessage());
                LOGGER.log(Level.SEVERE, "TESTAR MCP step failed", exception);
            }
        }

        mcpInterface.stopTestExecution();
        addInfoLog("maxAction executed");
        return "maxAction executed";
    }

    private String buildInstructions() {
        return "You are a BDD-GUI test agent. "
                + "Your goal is to complete the BDD instructions. "
                + "Use loadWebURL, getStateInteractiveWidgets, executeClickAction, executeFillAction, and executeSelectAction functions. "
                + "Use getCurrentURL and checkExecutedActions functions if you need assistance. "
                + "Use navigateBack function if you need to control the web browser. "
                + "After completing each BDD step (Given, When, Then), use getStateImage or getStateVisualText and addStepAssert functions to validate that step. "
                + "When asserting all BDD instructions, use the stopTestExecution function. "
                + "Choose exactly one tool invocation at a time. "
                + "If your provider requires text-only tool selection, return a single JSON object with this shape: "
                + "{\"thought\":\"...\",\"action\":{\"toolName\":\"...\",\"parameters\":{...}}}.";
    }

    private List<Map<String, Object>> defineInput() {
        List<Map<String, Object>> input = new ArrayList<>();
        input.add(createUserTextInput("Begin by load the web url to be tested."));
        input.add(createUserTextInput("Get the current GUI state to obtain the available web elements."));
        input.add(createUserTextInput(bddInstructions));
        return input;
    }

    private Map<String, Object> createUserTextInput(String text) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "input_text");
        content.put("text", text);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("role", "user");
        input.put("content", List.of(content));
        return input;
    }

    private static void attachStateImage(List<Map<String, Object>> inputItems, String base64Png) {
        Map<String, Object> text = new LinkedHashMap<>();
        text.put("type", "input_text");
        text.put("text", "Here is the current GUI state.");

        Map<String, Object> image = new LinkedHashMap<>();
        image.put("type", "input_image");
        image.put("image_url", "data:image/png;base64," + base64Png);
        image.put("detail", "high");

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("role", "user");
        input.put("content", List.of(text, image));
        inputItems.add(input);
    }

    private void addInfoLog(String msg) {
        LOGGER.log(Level.INFO, msg);
    }

    private void addSevereLog(String msg) {
        LOGGER.log(Level.SEVERE, msg);
    }

    private void logTokenUsage(int totalTokens) {
        if (totalTokens <= 0) {
            addInfoLog("DEBUG tokens step: usage not provided by provider");
            return;
        }

        addInfoLog("DEBUG tokens step: totalTokens=" + totalTokens);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

}
