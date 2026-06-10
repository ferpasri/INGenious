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
            String modelKey = String.valueOf(run.get("providerName"))
                    + "||" + String.valueOf(run.get("modelName"))
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
        return String.join("\n",
                "<!DOCTYPE html>",
                "<html>",
                "<head>",
                "  <meta charset=\"UTF-8\" />",
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />",
                "  <title>INGenious TESTAR MCP Dashboard</title>",
                "  <script src=\"mcp_metrics.js\"></script>",
                buildDashboardStyles(),
                "</head>",
                "<body>",
                "  <div id=\"app\"></div>",
                buildDashboardScript(),
                "</body>",
                "</html>");
    }

    /**
     * Keep the generated dashboard styling in one place so the HTML shell stays small.
     */
    private String buildDashboardStyles() {
        return String.join("\n",
                "  <style>",
                "    * { box-sizing: border-box; }",
                "    body { font-family: Segoe UI, Arial, sans-serif; margin: 0; color: #18212b; background: #f4f6f9; }",
                "    h1, h2, h3 { margin: 0; }",
                "    .page { max-width: 1360px; margin: 0 auto; padding: 24px; }",
                "    .hero { background: linear-gradient(135deg, #102f52, #1d5d94); color: #fff; border-bottom: 3px solid #2f7fc8; padding: 20px 24px; }",
                "    .hero-title { font-size: 18px; font-weight: 700; letter-spacing: .02em; }",
                "    .hero-subtitle { color: #d5e6f8; font-size: 13px; margin-top: 4px; }",
                "    .hero-badge { background: rgba(255,255,255,.14); color: #dcecff; border-radius: 999px; display: inline-block; font-size: 11px; font-weight: 700; margin-top: 10px; padding: 5px 10px; text-transform: uppercase; letter-spacing: .05em; }",
                "    .cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 14px; margin: 18px 0 22px 0; }",
                "    .card { background: #fff; border: 1px solid #d7deea; border-top: 3px solid #2f7fc8; border-radius: 8px; padding: 14px 16px; box-shadow: 0 6px 16px rgba(17, 37, 62, 0.06); }",
                "    .card.ok { border-top-color: #27844a; }",
                "    .card.warn { border-top-color: #d38b18; }",
                "    .card.bad { border-top-color: #b53c45; }",
                "    .card.neutral { border-top-color: #5c6c80; }",
                "    .card .label { color: #6b7890; font-size: 11px; font-weight: 700; letter-spacing: .07em; text-transform: uppercase; }",
                "    .card .value { color: #172331; font-size: 28px; font-weight: 700; margin-top: 6px; line-height: 1; }",
                "    .card .desc { color: #7d8b9f; font-size: 11px; margin-top: 6px; }",
                "    .section { background: #fff; border: 1px solid #d7deea; border-radius: 8px; box-shadow: 0 6px 16px rgba(17, 37, 62, 0.06); margin-bottom: 18px; overflow: hidden; }",
                "    .section-header { align-items: center; background: #f7f9fc; border-bottom: 1px solid #d7deea; display: flex; justify-content: space-between; gap: 12px; padding: 12px 18px; }",
                "    .section-title { color: #172331; font-size: 13px; font-weight: 700; letter-spacing: .03em; text-transform: uppercase; }",
                "    .section-meta { color: #7d8b9f; font-size: 11px; }",
                "    .section-body { padding: 18px; }",
                "    .chart-list { display: grid; gap: 12px; }",
                "    .chart-box { background: #f8fafd; border: 1px solid #e1e7f0; border-radius: 8px; padding: 12px; }",
                "    .chart-box h3 { color: #314258; font-size: 12px; font-weight: 700; margin-bottom: 8px; text-transform: uppercase; }",
                "    .model-group-layout { display: grid; grid-template-columns: 340px 1fr; gap: 18px; }",
                "    .model-group-list { display: grid; gap: 10px; }",
                "    .model-group-item { align-items: flex-start; background: #f8fafd; border: 1px solid #d8e1ee; border-radius: 8px; display: grid; gap: 10px; grid-template-columns: 24px 1fr; padding: 12px; }",
                "    .model-group-item.selected { background: #eef5fd; border-color: #7bb1ea; box-shadow: inset 0 0 0 1px #7bb1ea; }",
                "    .model-group-checkbox { margin-top: 2px; }",
                "    .model-group-button { background: transparent; border: none; color: inherit; cursor: pointer; padding: 0; text-align: left; width: 100%; }",
                "    .model-group-name { color: #1c2b3d; font-size: 13px; font-weight: 700; line-height: 1.4; }",
                "    .model-group-meta { color: #6f7f90; font-size: 11px; margin-top: 4px; }",
                "    .selected-group-header { border-bottom: 1px solid #e5ebf2; margin-bottom: 14px; padding-bottom: 12px; }",
                "    .selected-group-title { color: #1c2b3d; font-size: 16px; font-weight: 700; }",
                "    .selected-group-meta { color: #6f7f90; font-size: 12px; margin-top: 6px; }",
                "    .selected-group-panels { display: grid; gap: 14px; grid-template-columns: 1fr 1fr; margin-bottom: 14px; }",
                "    .bar-row { margin: 10px 0; }",
                "    .bar-label { color: #324055; display: flex; font-size: 12px; gap: 12px; justify-content: space-between; margin-bottom: 4px; }",
                "    .bar-track { background: #dfe6ef; border-radius: 999px; height: 10px; overflow: hidden; }",
                "    .bar-fill { background: linear-gradient(90deg, #226ec2, #53a7ff); border-radius: 999px; height: 100%; }",
                "    .bar-fill.warn { background: linear-gradient(90deg, #d18a18, #f3be58); }",
                "    .bar-fill.bad { background: linear-gradient(90deg, #ae3943, #ef7b84); }",
                "    table { width: 100%; border-collapse: collapse; }",
                "    th, td { border-bottom: 1px solid #e5ebf2; font-size: 13px; padding: 9px 8px; text-align: left; vertical-align: top; }",
                "    th { color: #55667d; font-size: 11px; font-weight: 700; letter-spacing: .05em; text-transform: uppercase; }",
                "    tbody tr:hover td { background: #fafcff; }",
                "    .pill { border-radius: 999px; display: inline-block; font-size: 11px; font-weight: 700; padding: 4px 9px; text-transform: uppercase; letter-spacing: .04em; }",
                "    .ok { background: #daf3e3; color: #1d7d47; }",
                "    .bad { background: #fbe3e5; color: #b52f3d; }",
                "    .tag { background: #e8edf6; border-radius: 4px; color: #314258; display: inline-block; font-size: 10px; font-weight: 700; padding: 3px 7px; text-transform: uppercase; letter-spacing: .04em; }",
                "    .mono { font-family: Consolas, monospace; font-size: 12px; }",
                "    .small { color: #6f7f90; font-size: 12px; }",
                "    .latency-mini { align-items: end; display: inline-flex; gap: 1px; height: 16px; }",
                "    .latency-mini span { background: #2c5f9e; border-radius: 1px 1px 0 0; display: inline-block; width: 3px; }",
                "    .empty { color: #8593a8; font-size: 13px; padding: 12px 0; }",
                "    @media (max-width: 1080px) { .model-group-layout, .selected-group-panels { grid-template-columns: 1fr; } }",
                "  </style>");
    }

    /**
     * The generated page uses plain browser APIs only. Splitting the script into helpers keeps
     * the Java side readable and avoids one giant string method.
     */
    private String buildDashboardScript() {
        return String.join("\n",
                "  <script>",
                buildDashboardUtilitiesScript(),
                buildDashboardRenderScript(),
                buildDashboardInitializationScript(),
                "  </script>");
    }

    private String buildDashboardUtilitiesScript() {
        return String.join("\n",
                "    const data = window.MCP_METRICS_DATA || { summary: {}, models: [], runs: [] };",
                "    const app = document.getElementById('app');",
                "    const state = { selectedGroupKey: '', comparedGroupKeys: [] };",
                "    function esc(value) { return String(value ?? '').replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;'); }",
                "    function intFmt(value) { return Number(value || 0).toLocaleString('en-US'); }",
                "    function pct(value) { return Number(value || 0).toFixed(1) + '%'; }",
                "    function decimalFmt(value) { return Number(value || 0).toFixed(2); }",
                "    function maxCount(map) { return Math.max(1, ...Object.values(map || {})); }",
                "    function safeArray(value) { return Array.isArray(value) ? value : []; }",
                "    function safeText(value, fallback) { return value ? String(value) : fallback; }",
                "    function isSuccess(run) { return !!run.completedSuccess; }",
                "    function groupKey(item) {",
                "      return [safeText(item.providerName, ''), safeText(item.modelName, ''), safeText(item.reasoningLevel, 'none'), item.visionEnabled ? 'vision-on' : 'vision-off'].join('||');",
                "    }",
                "    function groupLabel(item) {",
                "      return `${esc(item.providerName)} / <span class=\"mono\">${esc(item.modelName)}</span> / ${esc(item.reasoningLevel)} / ${item.visionEnabled ? 'Vision On' : 'Vision Off'}`;",
                "    }",
                "    function modelGroups() { return safeArray(data.models); }",
                "    function ensureSelectionState() {",
                "      const groups = modelGroups();",
                "      if (!groups.length) {",
                "        state.selectedGroupKey = '';",
                "        state.comparedGroupKeys = [];",
                "        return;",
                "      }",
                "      const firstKey = groupKey(groups[0]);",
                "      if (!state.selectedGroupKey || !groups.some(group => groupKey(group) === state.selectedGroupKey)) {",
                "        state.selectedGroupKey = firstKey;",
                "      }",
                "      const validKeys = groups.map(group => groupKey(group));",
                "      state.comparedGroupKeys = state.comparedGroupKeys.filter(key => validKeys.includes(key));",
                "    }",
                "    function selectedGroup() { return modelGroups().find(group => groupKey(group) === state.selectedGroupKey) || null; }",
                "    function selectedGroupRuns() { return safeArray(data.runs).filter(run => groupKey(run) === state.selectedGroupKey); }",
                "    function comparedGroups() { return modelGroups().filter(group => state.comparedGroupKeys.includes(groupKey(group))); }",
                "    function isCompared(key) { return state.comparedGroupKeys.includes(key); }",
                "    function selectGroup(key) {",
                "      state.selectedGroupKey = key;",
                "      renderPage();",
                "    }",
                "    function toggleComparedGroup(key) {",
                "      if (state.comparedGroupKeys.includes(key)) {",
                "        if (state.comparedGroupKeys.length == 1) {",
                "          return;",
                "        }",
                "        state.comparedGroupKeys = state.comparedGroupKeys.filter(entry => entry !== key);",
                "        if (state.selectedGroupKey === key) {",
                "          state.selectedGroupKey = state.comparedGroupKeys[0] || '';",
                "        }",
                "      } else {",
                "        state.comparedGroupKeys.push(key);",
                "      }",
                "      renderPage();",
                "    }",
                "    function maxMetric(items, key) { return Math.max(1, ...items.map(item => Number(item[key] || 0))); }",
                "    function barRows(map, style) {",
                "      const entries = Object.entries(map || {}).sort((left, right) => right[1] - left[1]);",
                "      const max = maxCount(map || {});",
                "      if (!entries.length) { return '<div class=\"empty\">No invalid actions recorded.</div>'; }",
                "      return entries.map(([label, value]) => {",
                "        const width = Math.max(4, Math.round((value / max) * 100));",
                "        return `<div class=\"bar-row\"><div class=\"bar-label\"><span>${esc(label)}</span><span>${intFmt(value)}</span></div><div class=\"bar-track\"><div class=\"bar-fill ${style || ''}\" style=\"width:${width}%\"></div></div></div>`;",
                "      }).join('');",
                "    }",
                "    function comparisonBars(items, key, format, style) {",
                "      if (!items.length) { return '<div class=\"empty\">No model data available.</div>'; }",
                "      const max = maxMetric(items, key);",
                "      return items.map(item => {",
                "        const width = Math.max(6, Math.round((Number(item[key] || 0) / max) * 100));",
                "        return `<div class=\"bar-row\"><div class=\"bar-label\"><span>${groupLabel(item)}</span><span>${format(item[key])}</span></div><div class=\"bar-track\"><div class=\"bar-fill ${style || ''}\" style=\"width:${width}%\"></div></div></div>`;",
                "      }).join('');",
                "    }",
                "    function latencyMini(run) {",
                "      const values = safeArray(run.latencyTimelineMs);",
                "      if (!values.length) { return '—'; }",
                "      const max = Math.max(1, ...values);",
                "      return `<div class=\"latency-mini\">${values.map(value => `<span style=\"height:${Math.max(3, Math.round((value / max) * 16))}px\"></span>`).join('')}</div>`;",
                "    }");
    }

    private String buildDashboardRenderScript() {
        return String.join("\n",
                "    function renderPage() {",
                "      ensureSelectionState();",
                "      app.innerHTML = `",
                "        <div class=\"hero\">",
                "          <div class=\"hero-title\">INGenious-TESTAR MCP Results Dashboard</div>",
                "          <div class=\"hero-subtitle\">Benchmark overview by model group and run batch.</div>",
                "          <div class=\"hero-badge\">${intFmt(safeArray(data.runs).length)} runs loaded</div>",
                "        </div>",
                "        <div class=\"page\">",
                "          ${renderOverview()}",
                "          ${renderModelGroupRunSection()}",
                "          ${renderBenchmarkComparison()}",
                "        </div>`;",
                "      attachGroupHandlers();",
                "    }",
                "    function renderOverview() {",
                "      const group = selectedGroup();",
                "      if (!group) { return '<div class=\"empty\">No model groups available.</div>'; }",
                "      return `",
                "        <div class=\"cards\">",
                "          <div class=\"card ${group.successRate >= 70 ? 'ok' : 'warn'}\"><div class=\"label\">Success Rate</div><div class=\"value\">${pct(group.successRate)}</div><div class=\"desc\">${groupLabel(group)}</div></div>",
                "          <div class=\"card ${Number(group.avgInvalidActions || 0) > 0 ? 'bad' : 'neutral'}\"><div class=\"label\">Average Invalid Actions</div><div class=\"value\">${decimalFmt(group.avgInvalidActions)}</div><div class=\"desc\">Mean invalid-action count for the selected model group.</div></div>",
                "          <div class=\"card warn\"><div class=\"label\">Average Latency</div><div class=\"value\">${intFmt(group.avgLatencyMs)} ms</div><div class=\"desc\">Mean latency across runs in this model group.</div></div>",
                "          <div class=\"card neutral\"><div class=\"label\">Average Tokens</div><div class=\"value\">${intFmt(group.avgTokens)}</div><div class=\"desc\">Mean total token consumption for this model group.</div></div>",
                "          <div class=\"card\"><div class=\"label\">Runs in Group</div><div class=\"value\">${intFmt(group.totalRuns)}</div><div class=\"desc\">${intFmt(group.successfulRuns)} successful runs inside the selected model group.</div></div>",
                "          <div class=\"card\"><div class=\"label\">Average Steps</div><div class=\"value\">${decimalFmt(group.avgSteps)}</div><div class=\"desc\">Mean executed steps across the selected model group.</div></div>",
                "        </div>`;",
                "    }",
                "    function renderModelGroupRunSection() {",
                "      const groups = modelGroups();",
                "      const group = selectedGroup();",
                "      const runs = selectedGroupRuns();",
                "      if (!groups.length || !group) { return '<div class=\"empty\">No model groups available.</div>'; }",
                "      const hasInvalidPatterns = Object.keys(group.invalidReasonCounts || {}).length > 0 || Object.keys(group.invalidActionsByTool || {}).length > 0;",
                "      const invalidDetail = hasInvalidPatterns",
                "        ? `<div class=\"selected-group-panels\"><div class=\"chart-box\"><h3>Invalid Action Patterns · By Reason</h3>${barRows(group.invalidReasonCounts || {}, 'bad')}</div><div class=\"chart-box\"><h3>Invalid Action Patterns · By Tool</h3>${barRows(group.invalidActionsByTool || {}, 'warn')}</div></div>`",
                "        : `<div class=\"chart-box\"><div class=\"small\"><strong>No invalid Action Patterns</strong></div></div>`;",
                "      return `",
                "        <div class=\"section\">",
                "          <div class=\"section-header\"><div class=\"section-title\">Model Group Run</div><div class=\"section-meta\">Select a model group to inspect its run history. Use checkboxes to include groups in the benchmark comparison below.</div></div>",
                "          <div class=\"section-body\">",
                "            <div class=\"model-group-layout\">",
                "              <div class=\"model-group-list\">",
                "                ${groups.map(entry => {",
                "                  const key = groupKey(entry);",
                "                  return `<div class=\"model-group-item ${key === state.selectedGroupKey ? 'selected' : ''}\">",
                "                    <input class=\"model-group-checkbox\" type=\"checkbox\" data-toggle-compare=\"${esc(key)}\" ${isCompared(key) ? 'checked' : ''} />",
                "                    <button class=\"model-group-button\" type=\"button\" data-select-group=\"${esc(key)}\">",
                "                      <div class=\"model-group-name\">${groupLabel(entry)}</div>",
                "                      <div class=\"model-group-meta\">Runs ${intFmt(entry.totalRuns)} · Success ${pct(entry.successRate)} · Avg invalids ${decimalFmt(entry.avgInvalidActions)}</div>",
                "                    </button>",
                "                  </div>`;",
                "                }).join('')}",
                "              </div>",
                "              <div>",
                "                <div class=\"selected-group-header\">",
                "                  <div class=\"selected-group-title\">${groupLabel(group)}</div>",
                "                  <div class=\"selected-group-meta\">${intFmt(group.totalRuns)} runs · ${pct(group.successRate)} success rate · ${intFmt(group.avgLatencyMs)} ms average latency · ${intFmt(group.avgTokens)} average tokens</div>",
                "                </div>",
                "                ${invalidDetail}",
                "                <div class=\"chart-box\" style=\"margin-top:14px\">",
                "                  <h3>Run Details</h3>",
                "                  ${runs.length ? `<table><thead><tr><th>Run</th><th>Status</th><th>Invalids</th><th>Latency</th><th>Tokens</th><th>Tool Calls</th><th>Steps</th><th>Timeline</th><th>Started</th></tr></thead><tbody>${runs.map(run => `<tr>",
                "                    <td>${esc(run.runName)}</td>",
                "                    <td><span class=\"pill ${isSuccess(run) ? 'ok' : 'bad'}\">${isSuccess(run) ? 'Success' : 'Failed'}</span></td>",
                "                    <td style=\"color:${Number(run.invalidActions || 0) > 0 ? '#a43641' : '#257c47'};font-weight:700\">${intFmt(run.invalidActions)}</td>",
                "                    <td class=\"mono\">${intFmt(run.avgLatencyMs)} ms</td>",
                "                    <td class=\"mono\">${intFmt(run.totalTokens)}</td>",
                "                    <td>${intFmt(run.toolCalls)}</td>",
                "                    <td>${intFmt(run.totalSteps)}</td>",
                "                    <td>${latencyMini(run)}</td>",
                "                    <td class=\"small\">${esc(run.startedAt)}</td>",
                "                  </tr>`).join('')}</tbody></table>` : '<div class=\"empty\">No runs available in this model group.</div>'}",
                "                </div>",
                "              </div>",
                "            </div>",
                "          </div>",
                "        </div>`;",
                "    }",
                "    function renderBenchmarkComparison() {",
                "      const groups = comparedGroups();",
                "      return `",
                "        <div class=\"section\">",
                "          <div class=\"section-header\"><div class=\"section-title\">Benchmark Comparison</div><div class=\"section-meta\">Dynamic comparison for the checked model groups.</div></div>",
                "          <div class=\"section-body chart-list\">",
                "            ${groups.length ? `",
                "              <div class=\"chart-box\"><h3>Success Rate</h3>${comparisonBars(groups, 'successRate', value => pct(value), '')}</div>",
                "              <div class=\"chart-box\"><h3>Average Invalid Actions</h3>${comparisonBars(groups, 'avgInvalidActions', value => decimalFmt(value), 'bad')}</div>",
                "              <div class=\"chart-box\"><h3>Average Latency (ms)</h3>${comparisonBars(groups, 'avgLatencyMs', value => intFmt(value) + ' ms', 'warn')}</div>",
                "              <div class=\"chart-box\"><h3>Average Tokens</h3>${comparisonBars(groups, 'avgTokens', value => intFmt(value), '')}</div>",
                "            ` : '<div class=\"empty\">Select one or more model groups to compare them here.</div>'}",
                "          </div>",
                "        </div>`;",
                "    }",
                "    function attachGroupHandlers() {",
                "      document.querySelectorAll('[data-select-group]').forEach(button => {",
                "        button.onclick = () => { selectGroup(button.getAttribute('data-select-group')); };",
                "      });",
                "      document.querySelectorAll('[data-toggle-compare]').forEach(checkbox => {",
                "        checkbox.onchange = event => { event.stopPropagation(); toggleComparedGroup(checkbox.getAttribute('data-toggle-compare')); };",
                "      });",
                "    }");
    }

    private String buildDashboardInitializationScript() {
        return String.join("\n",
                "    renderPage();");
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
