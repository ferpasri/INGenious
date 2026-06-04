# INGenious-TESTAR Studio vs CLI

This note compares the current INGenious-TESTAR execution paths:

- `Studio`: GUI-driven TESTAR MCP agent inside INGenious Studio
- `CLI`: daemon-backed `ingenious-cli.bat testar ...` command flow intended for agents

## Similarities

| Area | Studio | CLI | Notes |
| --- | --- | --- | --- |
| Browser/session engine | Yes | Yes | Both ultimately use the same Playwright-based TESTAR services. |
| State inspection | Yes | Yes | Both can inspect widgets, visible text, and screenshots. |
| Action execution | Yes | Yes | Both support click, fill, and select actions. |
| Assertions | Yes | Yes | Both can add assertion text tied to BDD steps. |
| Artifact generation | Yes | Yes | Both persist generated INGenious artifacts through `TESTARDataWriter` and persistence services. |
| Reusable components generation | Yes | Yes | Both rely on the same persistence path once actions/assertions are saved. |
| Parabank-style scenario generation | Yes | Yes | Both can generate reproducible test artifacts from the same target flow. |

## Differences

| Topic | Studio | CLI | Impact |
| --- | --- | --- | --- |
| Main entrypoint | `MCPAgentPanel` | `ingenious-cli.bat testar ...` | Studio is user-facing; CLI is agent-facing. |
| Orchestration style | LLM loop inside Studio | Explicit command sequence from the agent | Studio decides tool calls itself; CLI requires the agent to drive each step. |
| BDD source | User pastes or edits BDD text in the dialog | Agent should read scenario files from `.agents/bdd_goals` and pass `--bdd-goal` or `--bdd-file` | Both paths can operate with the full original BDD. |
| BDD validation | Yes | Yes | Studio uses `BddMcpValidator`; CLI validates `--bdd-step` against the loaded scenario in the backend. |
| Original-step enforcement | Yes | Yes | Both reject steps that do not match the original BDD instruction list. |
| Executed-step tracking | Yes | Yes | Studio and CLI both track executed BDD steps, but Studio applies it inside the in-IDE agent loop while CLI exposes it through `session status`. |
| Action history semantics | Basic internal context | BDD-aware CLI output | CLI `action history` shows which actions belong to which `bddStep`. |
| Quote normalization | Yes | Yes | Both normalize smart quotes before validation and persistence. |
| LLM/provider configuration | Yes | No | Studio exposes API URL, env var, model, reasoning, vision, max actions; CLI assumes the agent runtime is already configured externally. |
| Session lifetime | In-process inside Studio | Background local daemon | CLI keeps the session alive across separate commands. |
| Session visibility | Hidden behind dialog workflow | Explicit command state | CLI exposes `session status`, `action history`, `daemonPid`, `daemonActiveSession`, `scenarioTitle`, `scenarioSource`, and BDD step progress. |
| Project binding | Current open Studio project | Explicit `-p <projectPath>` on session start | CLI is more explicit and automation-friendly. |
| Retry policy | LLM prompt + UI loop | Agent policy + skill guidance | CLI behavior depends more on the external agent following the skill. |
| Image handling | Intended for LLM vision attachment | Writes a PNG file under `Dist\release\tmp\testar\<sessionId>\...` and returns `imagePath` | Studio stays web/LLM-oriented; CLI is local-agent oriented. |
| UI feedback | Notifications, busy dialog, saved settings, reusable tree reload | Terminal output | Studio is better for humans; CLI is better for scripted agent workflows. |

## Behavior that stay Studio-only

These parts are valuable in the GUI but do not need to be duplicated in the CLI path.

1. API/provider configuration form.
   Model, endpoint, key env var, reasoning, and vision flags belong naturally in the Studio panel for user workflows.

2. Busy dialog and notification UX.
   These are GUI concerns and add no value to agent-driven CLI execution.

3. Reusable tree reload and editor refresh.
   This is Studio state synchronization, not a CLI concern.

4. Human-oriented workflow around Save/Close.
   The CLI should remain direct and automation-friendly rather than mimicking Studio interaction patterns.

5. Embedded agent orchestration UI.
   The CLI should stay transport-oriented and command-oriented. The external agent already provides orchestration.
