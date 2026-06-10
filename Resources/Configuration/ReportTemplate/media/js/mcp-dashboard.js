/*
 * MCP Benchmark Dashboard
 *
 * This script renders the MCP benchmark view from the structured payload
 * written into mcp_metrics.js by the Java metrics writer.
 */
(function () {
    const data = window.MCP_METRICS_DATA || { summary: {}, models: [], runs: [] };
    const app = document.getElementById("app");
    const state = {
        selectedGroupKey: "",
        comparedGroupKeys: []
    };

    function esc(value) {
        return String(value ?? "")
                .replaceAll("&", "&amp;")
                .replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;");
    }

    function intFmt(value) {
        return Number(value || 0).toLocaleString("en-US");
    }

    function pct(value) {
        return Number(value || 0).toFixed(1) + "%";
    }

    function decimalFmt(value) {
        return Number(value || 0).toFixed(2);
    }

    function maxCount(map) {
        return Math.max(1, ...Object.values(map || {}));
    }

    function safeArray(value) {
        return Array.isArray(value) ? value : [];
    }

    function safeText(value, fallback) {
        return value ? String(value) : fallback;
    }

    function isSuccess(run) {
        return !!run.completedSuccess;
    }

    /* Build a stable identity for one benchmark configuration group. */
    function groupKey(item) {
        return [
            safeText(item.providerName, ""),
            safeText(item.modelName, ""),
            safeText(item.reasoningLevel, "none"),
            item.visionEnabled ? "vision-on" : "vision-off"
        ].join("||");
    }

    /* Human-readable label shown in group selectors and KPI cards. */
    function groupLabel(item) {
        return `${esc(item.providerName)} / <span class="mono">${esc(item.modelName)}</span> / ${esc(item.reasoningLevel)} / ${item.visionEnabled ? "Vision On" : "Vision Off"}`;
    }

    function modelGroups() {
        return safeArray(data.models);
    }

    function ensureSelectionState() {
        const groups = modelGroups();

        if (!groups.length) {
            state.selectedGroupKey = "";
            state.comparedGroupKeys = [];
            return;
        }

        const firstKey = groupKey(groups[0]);
        if (!state.selectedGroupKey || !groups.some(group => groupKey(group) === state.selectedGroupKey)) {
            state.selectedGroupKey = firstKey;
        }

        const validKeys = groups.map(group => groupKey(group));
        state.comparedGroupKeys = state.comparedGroupKeys.filter(key => validKeys.includes(key));
    }

    function selectedGroup() {
        return modelGroups().find(group => groupKey(group) === state.selectedGroupKey) || null;
    }

    function selectedGroupRuns() {
        return safeArray(data.runs).filter(run => groupKey(run) === state.selectedGroupKey);
    }

    function comparedGroups() {
        return modelGroups().filter(group => state.comparedGroupKeys.includes(groupKey(group)));
    }

    function isCompared(key) {
        return state.comparedGroupKeys.includes(key);
    }

    function selectGroup(key) {
        state.selectedGroupKey = key;
        renderPage();
    }

    function toggleComparedGroup(key) {
        if (state.comparedGroupKeys.includes(key)) {
            if (state.comparedGroupKeys.length === 1) {
                return;
            }

            state.comparedGroupKeys = state.comparedGroupKeys.filter(entry => entry !== key);
            if (state.selectedGroupKey === key) {
                state.selectedGroupKey = state.comparedGroupKeys[0] || "";
            }
        } else {
            state.comparedGroupKeys.push(key);
        }

        renderPage();
    }

    function maxMetric(items, key) {
        return Math.max(1, ...items.map(item => Number(item[key] || 0)));
    }

    /* Render one horizontal metric breakdown block. */
    function barRows(map, style) {
        const entries = Object.entries(map || {}).sort((left, right) => right[1] - left[1]);
        const max = maxCount(map || {});

        if (!entries.length) {
            return "<div class=\"empty\">No invalid actions recorded.</div>";
        }

        return entries.map(([label, value]) => {
            const width = Math.max(4, Math.round((value / max) * 100));
            return `<div class="bar-row"><div class="bar-label"><span>${esc(label)}</span><span>${intFmt(value)}</span></div><div class="bar-track"><div class="bar-fill ${style || ""}" style="width:${width}%"></div></div></div>`;
        }).join("");
    }

    /* Render bottom benchmark comparison bars for the selected groups. */
    function comparisonBars(items, key, format, style) {
        if (!items.length) {
            return "<div class=\"empty\">No model data available.</div>";
        }

        const max = maxMetric(items, key);
        return items.map(item => {
            const width = Math.max(6, Math.round((Number(item[key] || 0) / max) * 100));
            return `<div class="bar-row"><div class="bar-label"><span>${groupLabel(item)}</span><span>${format(item[key])}</span></div><div class="bar-track"><div class="bar-fill ${style || ""}" style="width:${width}%"></div></div></div>`;
        }).join("");
    }

    /* Render the tiny latency spark columns shown in run-detail rows. */
    function latencyMini(run) {
        const values = safeArray(run.latencyTimelineMs);

        if (!values.length) {
            return "—";
        }

        const max = Math.max(1, ...values);
        return `<div class="latency-mini">${values.map(value => `<span style="height:${Math.max(3, Math.round((value / max) * 16))}px"></span>`).join("")}</div>`;
    }

    function renderPage() {
        ensureSelectionState();
        app.innerHTML = `
        <div class="hero">
          <div class="hero-title">INGenious-TESTAR MCP Results Dashboard</div>
        </div>
        <div class="page">
          ${renderOverview()}
          ${renderModelGroupRunSection()}
          ${renderBenchmarkComparison()}
        </div>`;

        attachGroupHandlers();
    }

    /* KPI cards always reflect the currently selected model group. */
    function renderOverview() {
        const group = selectedGroup();

        if (!group) {
            return "<div class=\"empty\">No model groups available.</div>";
        }

        return `
        <div class="cards">
          <div class="card ${group.successRate >= 70 ? "ok" : "warn"}"><div class="label">Success Rate</div><div class="value">${pct(group.successRate)}</div><div class="desc">${groupLabel(group)}</div></div>
          <div class="card ${Number(group.avgInvalidActions || 0) > 0 ? "bad" : "neutral"}"><div class="label">Average Invalid Actions</div><div class="value">${decimalFmt(group.avgInvalidActions)}</div><div class="desc">Mean invalid-action count for the selected model group.</div></div>
          <div class="card warn"><div class="label">Average Latency</div><div class="value">${intFmt(group.avgLatencyMs)} ms</div><div class="desc">Mean latency across runs in this model group.</div></div>
          <div class="card neutral"><div class="label">Average Tokens</div><div class="value">${intFmt(group.avgTokens)}</div><div class="desc">Mean total token consumption for this model group.</div></div>
          <div class="card"><div class="label">Runs in Group</div><div class="value">${intFmt(group.totalRuns)}</div><div class="desc">${intFmt(group.successfulRuns)} successful runs inside the selected model group.</div></div>
          <div class="card"><div class="label">Average Steps</div><div class="value">${decimalFmt(group.avgSteps)}</div><div class="desc">Mean executed steps across the selected model group.</div></div>
        </div>`;
    }

    /*
     * Main interaction area:
     * - left column selects which model group is inspected
     * - right column shows invalid-action breakdown and run details for that group
     */
    function renderModelGroupRunSection() {
        const groups = modelGroups();
        const group = selectedGroup();
        const runs = selectedGroupRuns();

        if (!groups.length || !group) {
            return "<div class=\"empty\">No model groups available.</div>";
        }

        const hasInvalidPatterns = Object.keys(group.invalidReasonCounts || {}).length > 0
                || Object.keys(group.invalidActionsByTool || {}).length > 0;

        const invalidDetail = hasInvalidPatterns
                ? `<div class="selected-group-panels"><div class="chart-box"><h3>Invalid Action Patterns - By Reason</h3>${barRows(group.invalidReasonCounts || {}, "bad")}</div><div class="chart-box"><h3>Invalid Action Patterns - By Tool</h3>${barRows(group.invalidActionsByTool || {}, "warn")}</div></div>`
                : `<div class="chart-box"><div class="small"><strong>No invalid Action Patterns</strong></div></div>`;

        return `
        <div class="section">
          <div class="section-header"><div class="section-title">Model Group Run</div><div class="section-meta">Select a model group to inspect its run history. Use checkboxes to include groups in the benchmark comparison below.</div></div>
          <div class="section-body">
            <div class="model-group-layout">
              <div class="model-group-list">
                ${groups.map(entry => {
                    const key = groupKey(entry);
                    return `<div class="model-group-item ${key === state.selectedGroupKey ? "selected" : ""}">
                    <input class="model-group-checkbox" type="checkbox" data-toggle-compare="${esc(key)}" ${isCompared(key) ? "checked" : ""} />
                    <button class="model-group-button" type="button" data-select-group="${esc(key)}">
                      <div class="model-group-name">${groupLabel(entry)}</div>
                      <div class="model-group-meta">Runs ${intFmt(entry.totalRuns)} - Success ${pct(entry.successRate)} - Avg invalids ${decimalFmt(entry.avgInvalidActions)}</div>
                    </button>
                  </div>`;
                }).join("")}
              </div>
              <div>
                <div class="selected-group-header">
                  <div class="selected-group-title">${groupLabel(group)}</div>
                  <div class="selected-group-meta">${intFmt(group.totalRuns)} runs - ${pct(group.successRate)} success rate - ${intFmt(group.avgLatencyMs)} ms average latency - ${intFmt(group.avgTokens)} average tokens</div>
                </div>
                ${invalidDetail}
                <div class="chart-box" style="margin-top:14px">
                  <h3>Run Details</h3>
                  ${runs.length ? `<table><thead><tr><th>Run</th><th>Status</th><th>Invalids</th><th>Latency</th><th>Tokens</th><th>Tool Calls</th><th>Steps</th><th>Timeline</th><th>Started</th></tr></thead><tbody>${runs.map(run => `<tr>
                    <td>${esc(run.runName)}</td>
                    <td><span class="pill ${isSuccess(run) ? "ok" : "bad"}">${isSuccess(run) ? "Success" : "Failed"}</span></td>
                    <td style="color:${Number(run.invalidActions || 0) > 0 ? "#a43641" : "#257c47"};font-weight:700">${intFmt(run.invalidActions)}</td>
                    <td class="mono">${intFmt(run.avgLatencyMs)} ms</td>
                    <td class="mono">${intFmt(run.totalTokens)}</td>
                    <td>${intFmt(run.toolCalls)}</td>
                    <td>${intFmt(run.totalSteps)}</td>
                    <td>${latencyMini(run)}</td>
                    <td class="small">${esc(run.startedAt)}</td>
                  </tr>`).join("")}</tbody></table>` : `<div class="empty">No runs available in this model group.</div>`}
                </div>
              </div>
            </div>
          </div>
        </div>`;
    }

    /* Bottom section only compares the groups explicitly checked by the user. */
    function renderBenchmarkComparison() {
        const groups = comparedGroups();

        return `
        <div class="section">
          <div class="section-header"><div class="section-title">Benchmark Comparison</div><div class="section-meta">Dynamic comparison for the checked model groups.</div></div>
          <div class="section-body chart-list">
            ${groups.length ? `
              <div class="chart-box"><h3>Success Rate</h3>${comparisonBars(groups, "successRate", value => pct(value), "")}</div>
              <div class="chart-box"><h3>Average Invalid Actions</h3>${comparisonBars(groups, "avgInvalidActions", value => decimalFmt(value), "bad")}</div>
              <div class="chart-box"><h3>Average Latency (ms)</h3>${comparisonBars(groups, "avgLatencyMs", value => intFmt(value) + " ms", "warn")}</div>
              <div class="chart-box"><h3>Average Tokens</h3>${comparisonBars(groups, "avgTokens", value => intFmt(value), "")}</div>
            ` : `<div class="empty">Select one or more model groups to compare them here.</div>`}
          </div>
        </div>`;
    }

    /* Bind click handlers after each re-render because the DOM is replaced wholesale. */
    function attachGroupHandlers() {
        document.querySelectorAll("[data-select-group]").forEach(button => {
            button.onclick = () => {
                selectGroup(button.getAttribute("data-select-group"));
            };
        });

        document.querySelectorAll("[data-toggle-compare]").forEach(checkbox => {
            checkbox.onchange = event => {
                event.stopPropagation();
                toggleComparedGroup(checkbox.getAttribute("data-toggle-compare"));
            };
        });
    }

    renderPage();
})();
