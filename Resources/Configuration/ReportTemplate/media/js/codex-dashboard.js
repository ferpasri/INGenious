/*
 * Codex Results Dashboard
 *
 * This script renders Codex run metrics from the structured payload
 * written into codex_metrics.js by the Java dashboard writer.
 */
(function () {
    const data = window.CODEX_METRICS_DATA || { summary: {}, groups: [], runs: [] };
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

    function durationFmt(value) {
        return intFmt(value) + " ms";
    }

    function safeArray(value) {
        return Array.isArray(value) ? value : [];
    }

    function safeText(value, fallback) {
        return value ? String(value) : fallback;
    }

    function groupKey(item) {
        return [
            safeText(item.modelName, ""),
            safeText(item.reasoningEffort, "medium"),
            safeText(item.sandboxMode, "workspace-write"),
            safeText(item.approvalPolicy, "never"),
            item.networkAccessEnabled ? "network-on" : "network-off"
        ].join("||");
    }

    function groupLabel(item) {
        return `<span class="mono">${esc(item.modelName)}</span> / ${esc(item.reasoningEffort)} / ${esc(item.sandboxMode)} / ${esc(item.approvalPolicy)} / ${item.networkAccessEnabled ? "Network On" : "Network Off"}`;
    }

    function groups() {
        return safeArray(data.groups);
    }

    function ensureSelectionState() {
        const allGroups = groups();
        if (!allGroups.length) {
            state.selectedGroupKey = "";
            state.comparedGroupKeys = [];
            return;
        }

        const firstKey = groupKey(allGroups[0]);
        if (!state.selectedGroupKey || !allGroups.some(group => groupKey(group) === state.selectedGroupKey)) {
            state.selectedGroupKey = firstKey;
        }

        const validKeys = allGroups.map(group => groupKey(group));
        state.comparedGroupKeys = state.comparedGroupKeys.filter(key => validKeys.includes(key));
    }

    function selectedGroup() {
        return groups().find(group => groupKey(group) === state.selectedGroupKey) || null;
    }

    function selectedGroupRuns() {
        return safeArray(data.runs).filter(run => groupKey(run) === state.selectedGroupKey);
    }

    function comparedGroups() {
        return groups().filter(group => state.comparedGroupKeys.includes(groupKey(group)));
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
            state.comparedGroupKeys = state.comparedGroupKeys.filter(entry => entry !== key);
        } else {
            state.comparedGroupKeys.push(key);
        }
        renderPage();
    }

    function maxCount(map) {
        return Math.max(1, ...Object.values(map || {}));
    }

    function barRows(map, style) {
        const entries = Object.entries(map || {}).sort((left, right) => right[1] - left[1]);
        const max = maxCount(map || {});

        if (!entries.length) {
            return "<div class=\"empty\">No data recorded for this section.</div>";
        }

        return entries.map(([label, value]) => {
            const width = Math.max(4, Math.round((value / max) * 100));
            return `<div class="bar-row"><div class="bar-label"><span>${esc(label)}</span><span>${intFmt(value)}</span></div><div class="bar-track"><div class="bar-fill ${style || ""}" style="width:${width}%"></div></div></div>`;
        }).join("");
    }

    function comparisonBars(items, key, format, style) {
        if (!items.length) {
            return "<div class=\"empty\">Select one or more configuration groups to compare them here.</div>";
        }

        const max = Math.max(1, ...items.map(item => Number(item[key] || 0)));
        return items.map(item => {
            const width = Math.max(6, Math.round((Number(item[key] || 0) / max) * 100));
            return `<div class="bar-row"><div class="bar-label"><span>${groupLabel(item)}</span><span>${format(item[key])}</span></div><div class="bar-track"><div class="bar-fill ${style || ""}" style="width:${width}%"></div></div></div>`;
        }).join("");
    }

    function isSuccess(run) {
        return !!run.completedSuccess;
    }

    function finalResponseSummary(run) {
        const response = safeText(run.finalResponse, "");
        if (!response) {
            return "—";
        }
        return esc(response.length > 140 ? response.slice(0, 140) + "..." : response);
    }

    function renderPage() {
        ensureSelectionState();
        app.innerHTML = `
        <div class="hero">
          <div class="hero-title">INGenious-TESTAR Codex Results Dashboard</div>
        </div>
        <div class="page">
          ${renderOverview()}
          ${renderGroupRunSection()}
          ${renderBenchmarkComparison()}
        </div>`;

        attachHandlers();
    }

    function renderOverview() {
        const group = selectedGroup();
        if (!group) {
            return "<div class=\"empty\">No Codex runs available.</div>";
        }

        return `
        <div class="cards">
          <div class="card ${group.successRate >= 70 ? "ok" : "warn"}"><div class="label">Success Rate</div><div class="value">${pct(group.successRate)}</div><div class="desc">${groupLabel(group)}</div></div>
          <div class="card neutral"><div class="label">Average Total Tokens</div><div class="value">${intFmt(group.avgTotalTokens)}</div><div class="desc">Input + output + reasoning tokens for the selected configuration group.</div></div>
          <div class="card warn"><div class="label">Average Duration</div><div class="value">${durationFmt(group.avgDurationMs)}</div><div class="desc">Mean end-to-end Codex run duration.</div></div>
          <div class="card ${Number(group.avgFailedCommandCount || 0) > 0 ? "bad" : "ok"}"><div class="label">Average Command Failures</div><div class="value">${decimalFmt(group.avgFailedCommandCount)}</div><div class="desc">Mean failed shell command count per run.</div></div>
          <div class="card"><div class="label">Runs in Group</div><div class="value">${intFmt(group.totalRuns)}</div><div class="desc">${intFmt(group.successfulRuns)} successful runs inside the selected configuration group.</div></div>
          <div class="card"><div class="label">Average Completed Items</div><div class="value">${decimalFmt(group.avgCompletedItems)}</div><div class="desc">Mean number of completed Codex items per run.</div></div>
        </div>`;
    }

    function renderGroupRunSection() {
        const allGroups = groups();
        const group = selectedGroup();
        const runs = selectedGroupRuns();

        if (!allGroups.length || !group) {
            return "<div class=\"empty\">No configuration groups available.</div>";
        }

        return `
        <div class="section">
          <div class="section-header"><div class="section-title">Codex Configuration Group Runs</div><div class="section-meta">Select a configuration group to inspect Codex run history. Use checkboxes to include groups in the benchmark comparison below.</div></div>
          <div class="section-body">
            <div class="group-layout">
              <div class="group-list">
                ${allGroups.map(entry => {
                    const key = groupKey(entry);
                    return `<div class="group-item ${key === state.selectedGroupKey ? "selected" : ""}">
                      <input class="group-checkbox" type="checkbox" data-toggle-compare="${esc(key)}" ${isCompared(key) ? "checked" : ""} />
                      <button class="group-button" type="button" data-select-group="${esc(key)}">
                        <div class="group-name">${groupLabel(entry)}</div>
                        <div class="group-meta">Runs ${intFmt(entry.totalRuns)} - Success ${pct(entry.successRate)} - Avg tokens ${intFmt(entry.avgTotalTokens)}</div>
                      </button>
                    </div>`;
                }).join("")}
              </div>
              <div>
                <div class="selected-group-header">
                  <div class="selected-group-title">${groupLabel(group)}</div>
                  <div class="selected-group-meta">${intFmt(group.totalRuns)} runs - ${pct(group.successRate)} success rate - ${durationFmt(group.avgDurationMs)} average duration - ${intFmt(group.avgTotalTokens)} average total tokens</div>
                </div>
                <div class="selected-group-panels">
                  <div class="chart-box"><h3>Completed Item Mix</h3>${barRows(group.itemTypeCounts || {}, "")}</div>
                  <div class="chart-box"><h3>Run Outcomes</h3>${barRows({
                      successfulRuns: Number(group.successfulRuns || 0),
                      failedRuns: Math.max(0, Number(group.totalRuns || 0) - Number(group.successfulRuns || 0))
                  }, "warn")}</div>
                </div>
                <div class="chart-box" style="margin-top:14px">
                  <h3>Run Details</h3>
                  ${runs.length ? `<table><thead><tr><th>Run</th><th>Status</th><th>Duration</th><th>Total Tokens</th><th>Completed Items</th><th>Cmd Failures</th><th>Started</th><th>Final Response</th></tr></thead><tbody>${runs.map(run => `<tr>
                    <td>${esc(run.runName)}</td>
                    <td><span class="pill ${isSuccess(run) ? "ok" : "bad"}">${esc(run.status)}</span></td>
                    <td class="mono">${durationFmt(run.durationMs)}</td>
                    <td class="mono">${intFmt(run.totalTokens)}</td>
                    <td>${intFmt(run.completedItems)}</td>
                    <td style="color:${Number(run.failedCommandCount || 0) > 0 ? "#a43641" : "#257c47"};font-weight:700">${intFmt(run.failedCommandCount)}</td>
                    <td class="small">${esc(run.startedAt)}</td>
                    <td class="small">${finalResponseSummary(run)}</td>
                  </tr>`).join("")}</tbody></table>` : `<div class="empty">No runs available in this configuration group.</div>`}
                </div>
              </div>
            </div>
          </div>
        </div>`;
    }

    function renderBenchmarkComparison() {
        const selectedGroups = comparedGroups();
        return `
        <div class="section">
          <div class="section-header"><div class="section-title">Benchmark Comparison</div><div class="section-meta">Dynamic comparison for the checked Codex configuration groups.</div></div>
          <div class="section-body chart-list">
            ${selectedGroups.length ? `
              <div class="chart-box"><h3>Success Rate</h3>${comparisonBars(selectedGroups, "successRate", value => pct(value), "")}</div>
              <div class="chart-box"><h3>Average Total Tokens</h3>${comparisonBars(selectedGroups, "avgTotalTokens", value => intFmt(value), "warn")}</div>
              <div class="chart-box"><h3>Average Duration</h3>${comparisonBars(selectedGroups, "avgDurationMs", value => durationFmt(value), "warn")}</div>
              <div class="chart-box"><h3>Average Command Failures</h3>${comparisonBars(selectedGroups, "avgFailedCommandCount", value => decimalFmt(value), "bad")}</div>
            ` : `<div class="empty">Select one or more configuration groups to compare them here.</div>`}
          </div>
        </div>`;
    }

    function attachHandlers() {
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
