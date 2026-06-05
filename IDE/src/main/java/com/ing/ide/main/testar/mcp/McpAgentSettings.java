package com.ing.ide.main.testar.mcp;

public class McpAgentSettings {

    public String llmProviderName;
    public String apiUrl;
    public String apiKeyEnvVarName;
    public String openaiModel;
    public Boolean vision;
    public String reasoningLevel;
    public Integer maxActions;
    public Integer numRuns;
    public String bddScenarioName;
    public String bddInstructions;

    // Jackson no-arg constructor
    public McpAgentSettings() { }

}
