import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import vm from "node:vm";

const script = readFileSync(
    new URL("../../../../Resources/Configuration/ReportTemplate/media/js/agent-trace.js", import.meta.url),
    "utf8"
);

test("shows observations and actions in timestamp order with BDD labels", () => {
    const app = { innerHTML: "" };
    const handlers = new Map();
    const document = {
        getElementById(id) {
            if (id === "app") {
                return app;
            }
            return {
                addEventListener(type, handler) {
                    handlers.set(id + ":" + type, handler);
                },
            };
        },
    };
    const events = [
        { timestamp: "2026-09-28T10:00:03Z", source: "TESTAR", type: "OPERATION_COMPLETED",
            operation: "clickAction", bddStep: "When submit" },
        { timestamp: "2026-09-28T10:00:01Z", source: "TESTAR", type: "OPERATION_COMPLETED",
            operation: "firstObservation" },
        { timestamp: "2026-09-28T10:00:02Z", source: "MCP", type: "TOOL_REQUESTED",
            operation: "decideAction", bddStep: "When submit" },
        { timestamp: "2026-09-28T10:00:04Z", source: "TESTAR", type: "OPERATION_COMPLETED",
            operation: "secondObservation" },
    ];
    vm.runInNewContext(script, {
        window: { AGENT_TRACE_DATA: {
            events, runName: "test run", runId: "mcp-123", agentType: "mcp",
            modelGroup: { providerName: "OpenAI", modelName: "gpt-test",
                reasoningLevel: "low", visionEnabled: true }
        } }, document,
    });

    assert.ok(app.innerHTML.indexOf("firstObservation") < app.innerHTML.indexOf("decideAction"));
    assert.ok(app.innerHTML.indexOf("decideAction") < app.innerHTML.indexOf("clickAction"));
    assert.ok(app.innerHTML.indexOf("clickAction") < app.innerHTML.indexOf("secondObservation"));
    assert.match(app.innerHTML, /BDD step<\/span> When submit/);
    assert.equal((app.innerHTML.match(/BDD step<\/span>/g) ?? []).length, 2);
    assert.match(app.innerHTML, /<dt>Run<\/dt><dd>test run<\/dd>/);
    assert.match(app.innerHTML, /<dt>Model group<\/dt><dd>OpenAI \/ gpt-test \/ low \/ Vision On<\/dd>/);
    assert.match(app.innerHTML, /<dt>Trace ID<\/dt><dd class="trace-id">mcp-123<\/dd>/);

    handlers.get("trace-bdd-step:change")({ target: { value: "When submit" } });
    assert.ok(!app.innerHTML.includes("firstObservation"));
    assert.ok(app.innerHTML.includes("clickAction"));
});

test("shows Codex model group settings in the trace header", () => {
    const app = { innerHTML: "" };
    const document = {
        getElementById(id) {
            return id === "app" ? app : { addEventListener() {} };
        },
    };
    vm.runInNewContext(script, {
        window: { AGENT_TRACE_DATA: {
            events: [], runName: "Loan run", agentType: "codex",
            modelGroup: { modelName: "gpt-test", reasoningEffort: "medium",
                sandboxMode: "workspace-write", approvalPolicy: "never",
                networkAccessEnabled: false }
        } }, document,
    });

    assert.match(app.innerHTML, /gpt-test \/ medium \/ workspace-write \/ never \/ Network Off/);
});
