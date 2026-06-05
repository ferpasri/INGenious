package com.ing.ide.main.testar.mcp.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generate a standalone MCP metrics dashboard for benchmark-style run review.
 */
public class LlmMetricsDashboardWriter {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    public void writeDashboard(Path metricsRoot, List<Map<String, Object>> allRuns) throws IOException {
        Files.createDirectories(metricsRoot);

        Map<String, Object> dashboardData = buildDashboardData(allRuns);
        String jsPayload = "window.MCP_METRICS_DATA = " + JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(dashboardData) + ";";

        Files.writeString(metricsRoot.resolve("mcp_metrics.js"), jsPayload, StandardCharsets.UTF_8);
        Files.writeString(metricsRoot.resolve("dashboard.html"), buildDashboardHtml(), StandardCharsets.UTF_8);
    }

    private Map<String, Object> buildDashboardData(List<Map<String, Object>> allRuns) {
        List<Map<String, Object>> runs = new ArrayList<>(allRuns);
        runs.sort(Comparator.comparing(run -> String.valueOf(run.get("startedAt"))));

        Map<String, ModelAggregate> byModel = new LinkedHashMap<>();
        Map<String, Integer> invalidReasonCounts = new LinkedHashMap<>();
        Map<String, Integer> invalidToolCounts = new LinkedHashMap<>();

        for (Map<String, Object> run : runs) {
            String modelKey = String.valueOf(run.get("modelName"))
                    + "||" + String.valueOf(run.get("reasoningLevel"))
                    + "||" + String.valueOf(run.get("visionEnabled"));
            byModel.computeIfAbsent(modelKey, key -> new ModelAggregate(
                    String.valueOf(run.get("providerName")),
                    String.valueOf(run.get("modelName")),
                    String.valueOf(run.get("reasoningLevel")),
                    Boolean.TRUE.equals(run.get("visionEnabled"))
            )).accept(run);

            mergeCounts(invalidReasonCounts, mapValue(run.get("invalidActionReasonCounts")));
            mergeCounts(invalidToolCounts, mapValue(run.get("invalidActionsByTool")));
        }

        List<Map<String, Object>> models = new ArrayList<>();
        for (ModelAggregate aggregate : byModel.values()) {
            models.add(aggregate.toMap());
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalRuns", runs.size());
        summary.put("successfulRuns", countSuccess(runs));
        summary.put("failedRuns", runs.size() - countSuccess(runs));
        summary.put("invalidReasonCounts", invalidReasonCounts);
        summary.put("invalidActionsByTool", invalidToolCounts);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        payload.put("models", models);
        payload.put("runs", runs);
        return payload;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> mapValue(Object value) {
        if (!(value instanceof Map)) {
            return new LinkedHashMap<>();
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        ((Map<?, ?>) value).forEach((key, count) -> result.put(String.valueOf(key), intValue(count)));
        return result;
    }

    private void mergeCounts(Map<String, Integer> target, Map<String, Integer> source) {
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }

    private int countSuccess(List<Map<String, Object>> runs) {
        int success = 0;
        for (Map<String, Object> run : runs) {
            if (Boolean.TRUE.equals(run.get("completedSuccess"))) {
                success++;
            }
        }
        return success;
    }

    private int intValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception exception) {
            return 0;
        }
    }

    private long longValue(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception exception) {
            return 0L;
        }
    }

    private double rate(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0.0d;
        }
        return (100.0d * numerator) / denominator;
    }

    private String buildDashboardHtml() {
        return "<!DOCTYPE html>\n"
                + "<html>\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\" />\n"
                + "  <title>INGenious TESTAR MCP Dashboard</title>\n"
                + "  <script src=\"mcp_metrics.js\"></script>\n"
                + "  <style>\n"
                + "    body { font-family: Segoe UI, Arial, sans-serif; margin: 24px; color: #18212b; background: #f5f7fa; }\n"
                + "    h1, h2 { margin: 0 0 12px 0; }\n"
                + "    .hero { background: linear-gradient(135deg, #0a3d62, #195c92); color: #fff; border-radius: 12px; padding: 20px 24px; margin-bottom: 20px; }\n"
                + "    .hero p { margin: 6px 0 0 0; color: #d7e6f7; }\n"
                + "    .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 14px; margin: 18px 0 24px 0; }\n"
                + "    .card { background: #fff; border-radius: 10px; padding: 14px 16px; box-shadow: 0 6px 18px rgba(15, 35, 60, 0.08); }\n"
                + "    .card .label { font-size: 12px; color: #687789; text-transform: uppercase; letter-spacing: .06em; }\n"
                + "    .card .value { font-size: 28px; font-weight: 700; margin-top: 8px; }\n"
                + "    .section { background: #fff; border-radius: 12px; padding: 18px 20px; margin-bottom: 18px; box-shadow: 0 6px 18px rgba(15, 35, 60, 0.08); }\n"
                + "    table { width: 100%; border-collapse: collapse; }\n"
                + "    th, td { padding: 10px 8px; border-bottom: 1px solid #e4e8ee; text-align: left; font-size: 14px; vertical-align: top; }\n"
                + "    th { color: #516173; font-size: 12px; text-transform: uppercase; letter-spacing: .05em; }\n"
                + "    .pill { display: inline-block; padding: 4px 9px; border-radius: 999px; font-size: 12px; font-weight: 700; }\n"
                + "    .ok { background: #daf3e3; color: #1d7d47; }\n"
                + "    .bad { background: #fbe3e5; color: #b52f3d; }\n"
                + "    .bars { display: grid; grid-template-columns: repeat(auto-fit, minmax(240px, 1fr)); gap: 12px; }\n"
                + "    .bar-box { background: #f7f9fc; border-radius: 10px; padding: 12px; }\n"
                + "    .bar-row { margin: 10px 0; }\n"
                + "    .bar-label { display: flex; justify-content: space-between; gap: 12px; font-size: 13px; margin-bottom: 4px; }\n"
                + "    .bar-track { background: #dfe6ef; height: 10px; border-radius: 999px; overflow: hidden; }\n"
                + "    .bar-fill { background: linear-gradient(90deg, #1f78d1, #4fb0ff); height: 100%; border-radius: 999px; }\n"
                + "    .small { color: #6f7f90; font-size: 12px; }\n"
                + "    .mono { font-family: Consolas, monospace; font-size: 12px; }\n"
                + "  </style>\n"
                + "</head>\n"
                + "<body>\n"
                + "<div class=\"hero\"><h1>INGenious TESTAR MCP Dashboard</h1><p>Benchmark-oriented summary for Studio MCP runs.</p></div>\n"
                + "<div id=\"app\"></div>\n"
                + "<script>\n"
                + "  const data = window.MCP_METRICS_DATA || { summary: {}, models: [], runs: [] };\n"
                + "  const app = document.getElementById('app');\n"
                + "  function esc(v) { return String(v ?? '').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;'); }\n"
                + "  function pct(v) { return Number(v || 0).toFixed(1) + '%'; }\n"
                + "  function intFmt(v) { return Number(v || 0).toLocaleString('en-US'); }\n"
                + "  function maxCount(map) { return Math.max(1, ...Object.values(map || {})); }\n"
                + "  function barRows(map) {\n"
                + "    const entries = Object.entries(map || {}).sort((a,b) => b[1]-a[1]);\n"
                + "    const max = maxCount(map || {});\n"
                + "    if (!entries.length) return '<div class=\"small\">No invalid actions recorded.</div>';\n"
                + "    return entries.map(([k,v]) => {\n"
                + "      const width = Math.max(4, Math.round((v / max) * 100));\n"
                + "      return `<div class=\"bar-row\"><div class=\"bar-label\"><span>${esc(k)}</span><span>${intFmt(v)}</span></div><div class=\"bar-track\"><div class=\"bar-fill\" style=\"width:${width}%\"></div></div></div>`;\n"
                + "    }).join('');\n"
                + "  }\n"
                + "  function card(label, value) {\n"
                + "    return `<div class=\"card\"><div class=\"label\">${esc(label)}</div><div class=\"value\">${value}</div></div>`;\n"
                + "  }\n"
                + "  const summary = data.summary || {};\n"
                + "  const totalRuns = summary.totalRuns || 0;\n"
                + "  const successRate = totalRuns ? ((summary.successfulRuns || 0) * 100 / totalRuns) : 0;\n"
                + "  const overview = `\n"
                + "    <div class=\"cards\">\n"
                + "      ${card('Total Runs', intFmt(totalRuns))}\n"
                + "      ${card('Success Rate', pct(successRate))}\n"
                + "      ${card('Successful Runs', intFmt(summary.successfulRuns || 0))}\n"
                + "      ${card('Failed Runs', intFmt(summary.failedRuns || 0))}\n"
                + "      ${card('Models Compared', intFmt((data.models || []).length))}\n"
                + "    </div>`;\n"
                + "  const modelsTable = `\n"
                + "    <div class=\"section\"><h2>Model Comparison</h2>\n"
                + "    <table><thead><tr><th>Provider</th><th>Model</th><th>Reasoning</th><th>Vision</th><th>Runs</th><th>Success Rate</th><th>Avg Latency</th><th>Avg Tokens</th><th>Avg Invalid Actions</th><th>Avg Steps</th></tr></thead><tbody>\n"
                + "    ${(data.models || []).map(model => {\n"
                + "      const hasInvalidPatterns = Object.keys(model.invalidReasonCounts || {}).length > 0 || Object.keys(model.invalidActionsByTool || {}).length > 0;\n"
                + "      const invalidDetail = hasInvalidPatterns\n"
                + "        ? `<div><strong>Invalid Action Patterns</strong></div><div class=\"bars\" style=\"margin-top:8px\"><div class=\"bar-box\"><h3>By Reason</h3>${barRows(model.invalidReasonCounts || {})}</div><div class=\"bar-box\"><h3>By Tool</h3>${barRows(model.invalidActionsByTool || {})}</div></div>`\n"
                + "        : `<div class=\"small\"><strong>No invalid Action Patterns</strong></div>`;\n"
                + "      return `<tr>\n"
                + "      <td>${esc(model.providerName)}</td>\n"
                + "      <td class=\"mono\">${esc(model.modelName)}</td>\n"
                + "      <td>${esc(model.reasoningLevel)}</td>\n"
                + "      <td>${model.visionEnabled ? 'On' : 'Off'}</td>\n"
                + "      <td>${intFmt(model.totalRuns)}</td>\n"
                + "      <td><span class=\"pill ${model.successRate >= 70 ? 'ok' : 'bad'}\">${pct(model.successRate)}</span></td>\n"
                + "      <td>${intFmt(model.avgLatencyMs)} ms</td>\n"
                + "      <td>${intFmt(model.avgTokens)}</td>\n"
                + "      <td>${Number(model.avgInvalidActions || 0).toFixed(2)}</td>\n"
                + "      <td>${Number(model.avgSteps || 0).toFixed(2)}</td>\n"
                + "    </tr><tr><td colspan=\"10\" style=\"background:#fbfcfe;padding:14px 12px\">${invalidDetail}</td></tr>`;\n"
                + "    }).join('')}\n"
                + "    </tbody></table></div>`;\n"
                + "  const runsTable = `\n"
                + "    <div class=\"section\"><h2>Run Details</h2>\n"
                + "    <table><thead><tr><th>Run</th><th>Provider</th><th>Model</th><th>Reasoning</th><th>Vision</th><th>Status</th><th>Tokens</th><th>Latency</th><th>Steps</th><th>Invalids</th><th>Tool Calls</th><th>Started</th></tr></thead><tbody>\n"
                + "    ${(data.runs || []).map(run => `<tr>\n"
                + "      <td>${esc(run.runName)}</td>\n"
                + "      <td>${esc(run.providerName)}</td>\n"
                + "      <td class=\"mono\">${esc(run.modelName)}</td>\n"
                + "      <td>${esc(run.reasoningLevel || 'none')}</td>\n"
                + "      <td>${run.visionEnabled ? 'On' : 'Off'}</td>\n"
                + "      <td><span class=\"pill ${run.completedSuccess ? 'ok' : 'bad'}\">${run.completedSuccess ? 'Success' : 'Failed'}</span></td>\n"
                + "      <td>${intFmt(run.totalTokens)}</td>\n"
                + "      <td>${intFmt(run.avgLatencyMs)} ms</td>\n"
                + "      <td>${intFmt(run.totalSteps)}</td>\n"
                + "      <td>${intFmt(run.invalidActions)}</td>\n"
                + "      <td>${intFmt(run.toolCalls)}</td>\n"
                + "      <td class=\"small\">${esc(run.startedAt)}</td>\n"
                + "    </tr>`).join('')}\n"
                + "    </tbody></table></div>`;\n"
                + "  app.innerHTML = overview + modelsTable + runsTable;\n"
                + "</script>\n"
                + "</body>\n"
                + "</html>\n";
    }

    private final class ModelAggregate {

        private final String providerName;
        private final String modelName;
        private final String reasoningLevel;
        private final boolean visionEnabled;
        private final Map<String, Integer> invalidReasonCounts = new LinkedHashMap<>();
        private final Map<String, Integer> invalidActionsByTool = new LinkedHashMap<>();
        private int totalRuns;
        private int successfulRuns;
        private long totalLatencyMs;
        private long totalTokens;
        private long totalInvalidActions;
        private long totalSteps;

        private ModelAggregate(String providerName, String modelName, String reasoningLevel, boolean visionEnabled) {
            this.providerName = providerName;
            this.modelName = modelName;
            this.reasoningLevel = reasoningLevel;
            this.visionEnabled = visionEnabled;
        }

        private void accept(Map<String, Object> run) {
            totalRuns++;
            if (Boolean.TRUE.equals(run.get("completedSuccess"))) {
                successfulRuns++;
            }
            totalLatencyMs += longValue(run.get("avgLatencyMs"));
            totalTokens += longValue(run.get("totalTokens"));
            totalInvalidActions += longValue(run.get("invalidActions"));
            totalSteps += longValue(run.get("totalSteps"));
            mergeCounts(invalidReasonCounts, mapValue(run.get("invalidActionReasonCounts")));
            mergeCounts(invalidActionsByTool, mapValue(run.get("invalidActionsByTool")));
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("providerName", providerName);
            map.put("modelName", modelName);
            map.put("reasoningLevel", reasoningLevel);
            map.put("visionEnabled", visionEnabled);
            map.put("totalRuns", totalRuns);
            map.put("successfulRuns", successfulRuns);
            map.put("successRate", rate(successfulRuns, totalRuns));
            map.put("avgLatencyMs", totalRuns == 0 ? 0 : totalLatencyMs / totalRuns);
            map.put("avgTokens", totalRuns == 0 ? 0 : totalTokens / totalRuns);
            map.put("avgInvalidActions", totalRuns == 0 ? 0.0d : (double) totalInvalidActions / totalRuns);
            map.put("avgSteps", totalRuns == 0 ? 0.0d : (double) totalSteps / totalRuns);
            map.put("invalidReasonCounts", invalidReasonCounts);
            map.put("invalidActionsByTool", invalidActionsByTool);
            return map;
        }
    }
}
