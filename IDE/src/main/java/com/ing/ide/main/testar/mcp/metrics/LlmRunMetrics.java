package com.ing.ide.main.testar.mcp.metrics;

import com.ing.ide.main.testar.mcp.BddStepTracker;
import com.ing.ide.main.testar.mcp.McpAgentSettings;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collect in-memory metrics for one Studio TESTAR MCP run.
 *
 * <p>
 * The run identity intentionally reuses the generated BDD-MCP testcase name so
 * metrics can be correlated directly with the produced INGenious artifacts.
 * </p>
 */
public class LlmRunMetrics {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault());

    private final String runName;
    private final String projectName;
    private final String projectLocation;
    private final String providerName;
    private final String modelName;
    private final String reasoningLevel;
    private final boolean visionEnabled;
    private final String apiUrl;
    private final String scenarioName;
    private final String startedAt;
    private final List<Long> latencyTimelineMs = new ArrayList<>();
    private final List<LlmInvalidActionDetail> invalidActionDetails = new ArrayList<>();
    private final Map<String, Integer> invalidActionReasonCounts = new LinkedHashMap<>();
    private final Map<String, Integer> invalidActionsByTool = new LinkedHashMap<>();

    private String finishedAt = "";
    private boolean completedSuccess;
    private int totalSteps;
    private int invalidActions;
    private int totalTokens;
    private int retries;
    private int noToolResponses;
    private int toolCalls;
    private int visionRequests;
    private String failureMessage = "";
    private List<String> knownBddSteps = Collections.emptyList();
    private List<String> executedBddSteps = Collections.emptyList();
    private String latestExecutedBddStep = "";

    public LlmRunMetrics(String runName,
                         String projectName,
                         String projectLocation,
                         McpAgentSettings settings,
                         String providerName,
                         String modelName) {
        this.runName = runName != null ? runName : "";
        this.projectName = projectName != null ? projectName : "";
        this.projectLocation = projectLocation != null ? projectLocation : "";
        this.providerName = providerName != null ? providerName : "";
        this.modelName = modelName != null ? modelName : "";
        this.reasoningLevel = settings != null && settings.reasoningLevel != null ? settings.reasoningLevel : "none";
        this.visionEnabled = settings != null && settings.vision != null && settings.vision;
        this.apiUrl = settings != null && settings.apiUrl != null ? settings.apiUrl : "";
        this.scenarioName = settings != null && settings.bddScenarioName != null ? settings.bddScenarioName : "";
        this.startedAt = OffsetDateTime.now().toString();
    }

    public void recordPromptLatency(long latencyMs) {
        latencyTimelineMs.add(latencyMs);
    }

    public void addTokens(int tokenCount) {
        if (tokenCount > 0) {
            totalTokens += tokenCount;
        }
    }

    public void incrementRetry() {
        retries++;
    }

    public void incrementNoToolResponse() {
        noToolResponses++;
    }

    public void addToolCalls(int count) {
        if (count > 0) {
            toolCalls += count;
        }
    }

    public void incrementInvalidAction() {
        invalidActions++;
    }

    public void addInvalidActionDetail(String toolName,
                                       String bddStep,
                                       String selector,
                                       String value,
                                       String reasonCode,
                                       String reasonSummary,
                                       String feedbackMessage) {
        invalidActions++;
        invalidActionDetails.add(new LlmInvalidActionDetail(
                invalidActionDetails.size() + 1,
                toolName,
                bddStep,
                selector,
                value,
                reasonCode,
                reasonSummary,
                feedbackMessage
        ));
        invalidActionReasonCounts.merge(reasonCode, 1, Integer::sum);
        invalidActionsByTool.merge(toolName != null ? toolName : "", 1, Integer::sum);
    }

    public void incrementVisionRequest() {
        visionRequests++;
    }

    public void setBddProgress(BddStepTracker tracker) {
        if (tracker == null) {
            return;
        }
        knownBddSteps = new ArrayList<>(tracker.getOriginalBddSteps());
        executedBddSteps = new ArrayList<>(tracker.getExecutedBddSteps());
        latestExecutedBddStep = tracker.getLatestExecutedBddStep();
    }

    public void markSuccess(int executedSteps, BddStepTracker tracker) {
        completedSuccess = true;
        totalSteps = executedSteps;
        finishedAt = OffsetDateTime.now().toString();
        setBddProgress(tracker);
    }

    public void markFailure(int executedSteps, String message, BddStepTracker tracker) {
        completedSuccess = false;
        totalSteps = executedSteps;
        failureMessage = message != null ? message : "";
        finishedAt = OffsetDateTime.now().toString();
        setBddProgress(tracker);
    }

    public String getRunName() {
        return runName;
    }

    public String getRunFileSafeName() {
        return runName.replaceAll("[\\\\/?:*\"|><]", "_");
    }

    public String getProjectLocation() {
        return projectLocation;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getModelName() {
        return modelName;
    }

    public String getReasoningLevel() {
        return reasoningLevel;
    }

    public boolean isVisionEnabled() {
        return visionEnabled;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public String getFinishedAt() {
        return finishedAt;
    }

    public boolean isCompletedSuccess() {
        return completedSuccess;
    }

    public int getTotalSteps() {
        return totalSteps;
    }

    public int getInvalidActions() {
        return invalidActions;
    }

    public int getTotalTokens() {
        return totalTokens;
    }

    public int getRetries() {
        return retries;
    }

    public int getNoToolResponses() {
        return noToolResponses;
    }

    public int getToolCalls() {
        return toolCalls;
    }

    public int getVisionRequests() {
        return visionRequests;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public List<Long> getLatencyTimelineMs() {
        return Collections.unmodifiableList(latencyTimelineMs);
    }

    public long getAverageLatencyMs() {
        if (latencyTimelineMs.isEmpty()) {
            return 0L;
        }

        long totalLatency = 0L;
        for (Long latency : latencyTimelineMs) {
            totalLatency += latency;
        }
        return totalLatency / latencyTimelineMs.size();
    }

    public String getRunTimestamp() {
        return TIMESTAMP_FORMAT.format(OffsetDateTime.parse(startedAt));
    }

    public List<String> getKnownBddSteps() {
        return Collections.unmodifiableList(knownBddSteps);
    }

    public List<String> getExecutedBddSteps() {
        return Collections.unmodifiableList(executedBddSteps);
    }

    public String getLatestExecutedBddStep() {
        return latestExecutedBddStep;
    }

    public List<LlmInvalidActionDetail> getInvalidActionDetails() {
        return Collections.unmodifiableList(invalidActionDetails);
    }

    public Map<String, Integer> getInvalidActionReasonCounts() {
        return Collections.unmodifiableMap(invalidActionReasonCounts);
    }

    public Map<String, Integer> getInvalidActionsByTool() {
        return Collections.unmodifiableMap(invalidActionsByTool);
    }
}
