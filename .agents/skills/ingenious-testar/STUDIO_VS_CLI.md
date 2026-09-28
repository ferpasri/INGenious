# INGenious-TESTAR Execution Paths (Studio vs CLI)

This note compares the supported ways to generate INGenious TESTAR artifacts:

- `Studio MCP Agent`: the integrated, human-facing agent in INGenious Studio
- `ingenious-cli.bat`: the TESTAR command interface, driven manually or by an agent
- `ingenious-codex.bat`: the packaged Codex SDK runner that drives `ingenious-cli.bat`
- `External Codex`: a Codex environment outside INGenious that drives `ingenious-cli.bat`

The important distinction is that `ingenious-cli.bat` is not an LLM agent. It is the command
surface used to inspect the browser, execute actions, add assertions, and persist generated
artifacts. `ingenious-codex.bat` and external Codex environments are agent hosts that can use that
surface.

## Overview

| Topic | Studio MCP Agent | `ingenious-cli.bat` | `ingenious-codex.bat` | External Codex |
| --- | --- | --- | --- | --- |
| Primary role | Integrated agent and GUI | TESTAR command interface | Packaged Codex agent runner | Independently hosted Codex agent |
| Typical user | Studio user | Human operator, script, or agent | User launching the packaged agent | Developer using Codex in an IDE, terminal, or another environment |
| Agent included | Yes | No | Yes, through the Codex SDK | Yes, supplied by the external environment |
| TESTAR operations | Internal MCP tool calls | Explicit CLI commands | Codex executes CLI commands | Codex executes CLI commands |
| Agent instructions | Studio system prompt | Caller responsibility | Packaged execution prompt plus `ingenious-testar` skill | `ingenious-testar` skill plus the external prompt/context |
| LLM configuration | Studio form | Not applicable | Requested by `ingenious-codex.bat` | Configured in the external Codex environment |
| Session process | In-process Studio workflow | Local TESTAR daemon | Local TESTAR daemon through the CLI | Local TESTAR daemon through the CLI |
| Token usage captured by INGenious | Yes, for the Studio MCP run | No | Yes, for the complete Codex run | No automatic INGenious capture |
| Results dashboard | `MCP Results` | None for LLM metrics | `Codex Results` | External tooling unless compatible results are imported |
| Generated INGenious artifacts | Yes | Yes | Yes, through the CLI | Yes, through the CLI |

## Shared TESTAR Capabilities

All paths ultimately use the same Playwright-based TESTAR backend and persistence services.

| Area | Supported behavior |
| --- | --- |
| State inspection | Inspect interactive widgets, visible text, page titles, and screenshots. |
| Action execution | Execute click, check, fill, and select actions. |
| Assertions | Add visible-text and page-title assertions associated with BDD steps. |
| BDD validation | Validate supplied BDD steps against the loaded scenario. |
| Progress tracking | Track known, executed, and latest valid BDD steps. |
| Artifact generation | Persist generated scenarios, reusable components, object repository entries, and test data. |

The execution path changes who makes decisions and which telemetry is available. It does not
change the underlying TESTAR action and persistence services.

## Studio MCP Agent

The Studio path is a self-contained, user-facing workflow:

- The user selects a project and supplies the BDD scenario in Studio.
- Studio configures the provider, model, reasoning level, vision, and maximum actions.
- The integrated LLM loop invokes TESTAR MCP tools directly.
- Studio provides notifications, busy-state handling, and project-tree refreshes.
- MCP run metrics can include provider, model, reasoning, vision, duration, token usage, valid and
  invalid actions, and invalid-action classifications.
- The `MCP Results` menu opens the dashboard generated from the Studio MCP run files.

This path is appropriate when a human wants to configure and supervise generation from the GUI.

## Direct `ingenious-cli.bat`

The CLI is the lowest-level public automation interface:

- It exposes session, navigation, state, action, assertion, status, and history commands.
- It starts a local daemon automatically and preserves the browser session across commands.
- The caller is responsible for choosing the next command and following the skill rules.
- It has no knowledge of the caller's provider, model, reasoning effort, prompt context, or token
  consumption.
- It therefore cannot calculate LLM token metrics by itself.
- It still produces INGenious test artifacts and exposes BDD-aware session and action history.

Use this path for deterministic scripts, manual diagnostics, or as the command interface for any
compatible agent.

## Packaged `ingenious-codex.bat`

`ingenious-codex.bat` is the packaged agent entrypoint. It is not an alternative implementation of
TESTAR commands; it hosts Codex and instructs Codex to operate `ingenious-cli.bat`.

The launcher requests the project, API environment variable, model, reasoning effort, sandbox,
approval policy, network setting, run title, and task prompt. It then:

1. Starts a Codex SDK run from the packaged runtime root.
2. Supplies packaged-runtime and `ingenious-testar` skill context.
3. Streams Codex messages and command activity to the terminal.
4. Lets Codex inspect state and execute TESTAR CLI commands autonomously.
5. Stores the run under `Projects\<project>\Results\Codex\runs\<run-name>`.

Each run folder contains:

- `request.json`: requested Codex settings and prompt
- `events.jsonl`: streamed Codex events and completed items
- `result.json`: final status, response, completed items, and run-level token usage

The captured usage includes input, cached input, output, and reasoning-output tokens for the whole
Codex run. It is run-level usage, not token usage for each individual TESTAR command. Studio can
aggregate these files through the `Codex Results` dashboard.

Use this path when reproducible INGenious-owned Codex configuration, terminal visibility, and
token reporting are required.

## External Codex Environment

An external Codex environment can run the same TESTAR workflow without
`ingenious-codex.bat`. For example, Codex running in an IDE can read the `ingenious-testar` skill
and invoke `ingenious-cli.bat` from the packaged runtime root.

Compared with the packaged runner:

- Agent configuration, permissions, conversation context, and lifecycle belong to the external
  Codex environment.
- The external agent can use the same BDD goals, commands, daemon, and generated artifacts.
- INGenious sees the resulting CLI requests, but it does not own the surrounding Codex run.
- INGenious does not automatically create `Results\Codex\runs` telemetry or capture the external
  agent's token usage.
- The external platform may display its own token usage, but those metrics are not automatically
  available to the INGenious `Codex Results` dashboard.

Use this path for development and interactive agent work when INGenious-managed Codex telemetry is
not required. Use `ingenious-codex.bat` when run-level token measurement is part of the experiment
or benchmark.

## Studio Versus CLI Behavior

| Topic | Studio MCP Agent | CLI-based paths | Impact |
| --- | --- | --- | --- |
| BDD source | User pastes or edits BDD text in Studio | Agent reads `.agents/bdd_goals` and passes `--bdd-goal` or `--bdd-file` | Both can use the full original BDD. |
| BDD validation | `BddMcpValidator` | Backend validation against the loaded scenario | Both reject unknown or stale BDD-step mappings. |
| Action history | Internal agent context | `testar action history` with associated `bddStep` values | CLI history is directly inspectable by the caller. |
| Radio and checkbox handling | MCP action path | Explicit `testar action check` | Successful checked state is verified before persistence. |
| Image handling | Base64 image for the integrated LLM workflow | PNG under `tmp\testar\<sessionId>` with a returned `imagePath` | CLI-oriented agents can inspect a local file. |
| Project binding | Current Studio project | Explicit `-p=<projectPath>` at session start | CLI runs are explicit and automation-friendly. |
| Retry decisions | Integrated LLM loop | Packaged or external agent policy guided by the skill | Reliability depends on the driving agent following the command contract. |
| Human feedback | Dialogs, notifications, and editor refresh | Terminal and structured command output | Studio is human-oriented; CLI paths are automation-oriented. |

## Choosing a Path

- Use the `Studio MCP Agent` for an integrated human workflow with Studio MCP metrics.
- Use `ingenious-cli.bat` directly for manual commands, deterministic automation, or integration
  with a non-Codex agent.
- Use `ingenious-codex.bat` when INGenious should launch Codex and retain run configuration,
  events, results, and token usage.
- Use an external Codex environment when the surrounding development environment should own the
  agent session and INGenious token telemetry is not required.

Do not compare Studio MCP metrics and Codex metrics as if they contained identical fields. Studio
controls each LLM/tool exchange and can collect detailed action-level metrics. The packaged Codex
runner observes the Codex SDK run and currently records aggregate token usage and completed-item
telemetry for the complete run.
