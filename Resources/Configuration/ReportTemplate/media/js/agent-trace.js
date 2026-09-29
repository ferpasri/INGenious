/*
 * Shared MCP and Codex trace timeline.
 * The Java writer normalizes runtime-specific events into this page's payload.
 */
(function () {
    const payload = window.AGENT_TRACE_DATA || { title: "Agent trace", events: [] };
    const app = document.getElementById("app");
    const events = (Array.isArray(payload.events) ? payload.events : []).slice().sort((left, right) => {
        const leftTime = Date.parse(left.timestamp || "");
        const rightTime = Date.parse(right.timestamp || "");
        if (Number.isFinite(leftTime) && Number.isFinite(rightTime) && leftTime !== rightTime) {
            return leftTime - rightTime;
        }
        if (Number.isFinite(leftTime) !== Number.isFinite(rightTime)) {
            return Number.isFinite(leftTime) ? -1 : 1;
        }
        return Number(left.viewerSequence || left.sequence || 0)
                - Number(right.viewerSequence || right.sequence || 0);
    });
    const filters = { query: "", bddStep: "", source: "", type: "", status: "" };

    function esc(value) {
        return String(value ?? "")
                .replaceAll("&", "&amp;")
                .replaceAll("<", "&lt;")
                .replaceAll(">", "&gt;")
                .replaceAll('"', "&quot;")
                .replaceAll("'", "&#39;");
    }

    function eventStatus(event) {
        return String(event.status || "INFO").toLowerCase();
    }

    function eventTitle(event) {
        return String(event.operation || event.type || "Event").replaceAll("_", " ").trim();
    }

    function eventDetails(event) {
        const details = {};
        ["arguments", "feedback", "evidence", "artifacts", "operationId", "parentEventId", "callId", "raw"]
                .forEach(key => {
                    if (event[key] !== undefined && event[key] !== null && event[key] !== "") {
                        details[key] = event[key];
                    }
                });
        if (event.exitCode !== undefined && event.exitCode !== -1) {
            details.exitCode = event.exitCode;
        }
        if (event.commandStatus) {
            details.commandStatus = event.commandStatus;
        }
        return details;
    }

    function displayTime(event) {
        const timestamp = String(event.timestamp || "");
        if (!timestamp) {
            return "#" + String(event.viewerSequence || event.sequence || "");
        }
        const date = new Date(timestamp);
        return Number.isNaN(date.getTime())
                ? timestamp
                : date.toLocaleTimeString([], {
                    hour: "2-digit", minute: "2-digit", second: "2-digit",
                    fractionalSecondDigits: 3
                });
    }

    function modelGroupLabel() {
        const group = payload.modelGroup || {};
        if (!group.modelName) {
            return "Unavailable";
        }
        if (payload.agentType === "mcp") {
            return [
                group.providerName || "Unknown provider",
                group.modelName,
                group.reasoningLevel || "none",
                group.visionEnabled ? "Vision On" : "Vision Off"
            ].join(" / ");
        }
        return [
            group.modelName,
            group.reasoningEffort || "medium",
            group.sandboxMode || "workspace-write",
            group.approvalPolicy || "never",
            group.networkAccessEnabled ? "Network On" : "Network Off"
        ].join(" / ");
    }

    function filteredEvents() {
        const query = filters.query.toLowerCase();
        return events.filter(event => {
            const searchable = [
                event.type, event.source, event.operation, event.message, event.bddStep,
                JSON.stringify(event.arguments || {}), JSON.stringify(event.artifacts || {})
            ].join(" ").toLowerCase();
            return (!query || searchable.includes(query))
                    && (!filters.source || String(event.source) === filters.source)
                    && (!filters.type || String(event.type) === filters.type)
                    && (!filters.status || String(event.status) === filters.status)
                    && (!filters.bddStep || String(event.bddStep || "") === filters.bddStep);
        });
    }

    function renderEvent(event) {
        const details = eventDetails(event);
        const operationId = event.operationId
                ? '<span class="badge">op ' + esc(String(event.operationId).slice(0, 8)) + '</span>'
                : "";
        const detailsMarkup = Object.keys(details).length
                ? '<details><summary>Arguments, feedback, and artifacts</summary><pre>' +
                    esc(JSON.stringify(details, null, 2)) + '</pre></details>'
                : "";
        return '<article class="event">' +
            '<time class="event-time">' + esc(displayTime(event)) + '</time>' +
            '<div class="event-main">' +
              '<div class="event-title"><span>' + esc(eventTitle(event)) + '</span>' +
                '<span class="badge">' + esc(event.source || "TRACE") + '</span>' +
                '<span class="badge ' + esc(eventStatus(event)) + '">' + esc(event.status || "INFO") + '</span>' +
                operationId +
              '</div>' +
              (event.bddStep ? '<div class="event-bdd"><span>BDD step</span> ' +
                  esc(event.bddStep) + '</div>' : "") +
              (event.message ? '<div class="event-message">' + esc(event.message) + '</div>' : "") +
              detailsMarkup +
            '</div>' +
          '</article>';
    }

    function render() {
        const selected = filteredEvents();
        const bddSteps = [...new Set(events.map(event => String(event.bddStep || "")).filter(Boolean))];
        const sources = [...new Set(events.map(event => String(event.source || "")).filter(Boolean))];
        const types = [...new Set(events.map(event => String(event.type || "")).filter(Boolean))];
        const statuses = [...new Set(events.map(event => String(event.status || "")).filter(Boolean))];
        const failed = events.filter(event => ["FAILED", "REJECTED"].includes(String(event.status))).length;
        const artifacts = events.filter(event => String(event.type).startsWith("ARTIFACT_")).length;

        app.innerHTML =
          '<header class="hero"><h1>' + esc(payload.title || "Agent trace") + '</h1>' +
          '<dl class="run-context">' +
            '<div><dt>Run</dt><dd>' + esc(payload.runName || payload.runId || "Unavailable") + '</dd></div>' +
            '<div><dt>Model group</dt><dd>' + esc(modelGroupLabel()) + '</dd></div>' +
            (payload.runId ? '<div><dt>Trace ID</dt><dd class="trace-id">' + esc(payload.runId) + '</dd></div>' : '') +
          '</dl></header>' +
          '<div class="page">' +
            '<section class="summary">' +
              '<div class="summary-card"><div class="summary-label">Events</div><div class="summary-value">' + events.length + '</div></div>' +
              '<div class="summary-card"><div class="summary-label">BDD steps</div><div class="summary-value">' + bddSteps.length + '</div></div>' +
              '<div class="summary-card"><div class="summary-label">Feedback issues</div><div class="summary-value">' + failed + '</div></div>' +
              '<div class="summary-card"><div class="summary-label">Artifact events</div><div class="summary-value">' + artifacts + '</div></div>' +
            '</section>' +
            '<section class="toolbar" aria-label="Trace filters">' +
              '<input type="search" id="trace-search" placeholder="Search events, selectors, BDD steps..." value="' + esc(filters.query) + '">' +
              renderSelect("trace-bdd-step", "All BDD steps", bddSteps, filters.bddStep) +
              renderSelect("trace-source", "All sources", sources, filters.source) +
              renderSelect("trace-type", "All event types", types, filters.type) +
              renderSelect("trace-status", "All statuses", statuses, filters.status) +
            '</section>' +
            (selected.length
                ? '<section class="timeline"><div class="timeline-heading">Chronological events' +
                    ' <span class="badge">' + selected.length + ' events</span></div>' +
                    '<div class="event-list">' + selected.map(renderEvent).join("") + '</div></section>'
                : '<div class="empty">No trace events match these filters.</div>') +
          '</div>';
        attachHandlers();
    }

    function renderSelect(id, label, values, selected) {
        return '<select id="' + id + '"><option value="">' + label + '</option>' +
            values.map(value => '<option value="' + esc(value) + '"' +
                (selected === value ? ' selected' : '') + '>' + esc(value) + '</option>').join("") +
            '</select>';
    }

    function attachHandlers() {
        document.getElementById("trace-search").addEventListener("input", event => {
            filters.query = event.target.value;
            render();
            const search = document.getElementById("trace-search");
            search.focus();
            search.setSelectionRange(search.value.length, search.value.length);
        });
        document.getElementById("trace-source").addEventListener("change", event => {
            filters.source = event.target.value;
            render();
        });
        document.getElementById("trace-bdd-step").addEventListener("change", event => {
            filters.bddStep = event.target.value;
            render();
        });
        document.getElementById("trace-type").addEventListener("change", event => {
            filters.type = event.target.value;
            render();
        });
        document.getElementById("trace-status").addEventListener("change", event => {
            filters.status = event.target.value;
            render();
        });
    }

    render();
})();
