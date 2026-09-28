package com.ing.ide.main.testar.mcp;

import com.ing.ide.main.testar.mcp.helper.McpMethod;
import com.ing.ide.main.testar.mcp.helper.McpParam;
import com.ing.ide.main.testar.mcp.helper.McpToolBuilder;
import com.ing.ide.main.testar.mcp.helper.McpToolExecutor;

/**
 * Contract for all MCP tools exposed to the LLM agent.
 * <p>
 * Methods annotated with {@link McpMethod} becomes a callable "tool" in the Chat Completions API.
 * The {@link McpToolBuilder} reflects this interface to produce the tool definitions (name, description, JSON schema).
 * The {@link McpToolExecutor} dispatches tool calls to the implementing driver (e.g., {@link PlaywrightMcpDriver}).
 * <p>
 * Rules:
 * - Annotate each method with {@link McpMethod} to be visible to the agent.
 * - Annotate each parameter with {@link McpParam} to define its JSON name, description, and whether it is required.
 * - Write clear, action-oriented descriptions; the agent relies on them for intent and argument selection.
 * - Return values should be concise and produced through {@link Feedback} to keep ISSUE/valid formatting consistent.
 */
public interface McpInterface {
    String bddStepDescription = "The BDD step text associated with this action. Do not modify.";

    @McpMethod(description = "Load the web URL to be tested.")
    Feedback loadWebURL(
        @McpParam(name = "bddStep", description = bddStepDescription) String bddStep,
        @McpParam(name = "url", description = "The web url to be tested.") String url
    );

    @McpMethod(description = "Get the current web url that is being tested.")
    Feedback getCurrentURL();

    @McpMethod(description = "Navigate back to the previous web page.")
    Feedback navigateBack();

    @McpMethod(
        description = "Get a list of current interactive GUI web elements with CSS selector, visible text, tag type, and accessibility attributes."
    )
    Feedback getStateInteractiveWidgets();

    @McpMethod(description = "Use a CSS selector to click an element.")
    Feedback executeClickAction(
        @McpParam(name = "bddStep", description = bddStepDescription) String bddStep,
        @McpParam(
            name = "cssSelector",
            description = "The CSS selector of the clickable element."
        ) String cssSelector
    );

    @McpMethod(
        description = "Use a CSS selector to check a radio button or checkbox. Use this instead of click for radio and checkbox inputs."
    )
    Feedback executeCheckAction(
        @McpParam(name = "bddStep", description = bddStepDescription) String bddStep,
        @McpParam(
            name = "cssSelector",
            description = "The CSS selector of the radio button or checkbox input."
        ) String cssSelector
    );

    @McpMethod(description = "Use a CSS selector to fill text into an input element.")
    Feedback executeFillAction(
        @McpParam(name = "bddStep", description = bddStepDescription) String bddStep,
        @McpParam(
            name = "cssSelector",
            description = "The CSS selector of the input element to be filled."
        ) String cssSelector,
        @McpParam(
            name = "fillText",
            description = "The text to be entered into the input element."
        ) String fillText
    );

    @McpMethod(description = "Use a CSS selector to select an option value into a select element.")
    Feedback executeSelectAction(
        @McpParam(name = "bddStep", description = bddStepDescription) String bddStep,
        @McpParam(
            name = "cssSelector",
            description = "The CSS selector of the select element to be changed."
        ) String cssSelector,
        @McpParam(
            name = "optionValue",
            description = "The option value to be selected into the select element."
        ) String optionValue
    );

    @McpMethod(
        description = "Check the information of the executed actions during the BDD test process."
    )
    Feedback checkExecutedActions();

    @McpMethod(description = "Get an image of the current GUI state.")
    Feedback getStateImage();

    @McpMethod(description = "Get a list of the text of all current visible GUI web elements.")
    Feedback getStateVisualText();

    @McpMethod(description = "Get the current web state-page title.")
    Feedback getStateTitle();

    @McpMethod(description = "Add a unique text that can be used to assert a BDD step.")
    Feedback addStepAssert(
        @McpParam(name = "bddStep", description = bddStepDescription) String bddStep,
        @McpParam(
            name = "assertText",
            description = "A visible unique text that can be used as text locator assert."
        ) String assertText
    );

    @McpMethod(description = "Add a state-page title pattern assertion for a BDD step.")
    Feedback addTitleAssert(
        @McpParam(name = "bddStep", description = bddStepDescription) String bddStep,
        @McpParam(
            name = "titlePattern",
            description = "A Java regular expression matching the current and expected state-page title."
        ) String titlePattern
    );

    @McpMethod(
        description = "Stop the test execution when the final assert is completed successfully."
    )
    void stopTestExecution();
}
