---
name: ingenious-testar
description: "Use when operating the INGenious TESTAR packaged CLI to create scriptless sessions, inspect state, execute actions, add assertions, and generate INGenious artifacts."
---

# INGenious TESTAR

Use this skill for the INGenious packaged TESTAR CLI integration.

Assume the agent is already running from the packaged runtime root.

Use these runtime-local paths:

- preferred CLI launcher: `ingenious-cli.bat`
- preferred BDD goal root: `.agents/bdd_goals`

## When to use

- The task requires driving a live web session through INGenious TESTAR CLI commands.
- The task requires generating INGenious test artifacts from a scriptless TESTAR session.
- The task requires step-by-step UI inspection, action execution, and assertion creation.

## Public launcher

Use the packaged Windows launcher:

- `ingenious-cli.bat`

The TESTAR CLI auto-starts a local daemon on first use. The daemon keeps the active TESTAR session alive across separate CLI commands.

## Project path rules

- Pass projects relative to the packaged runtime root, for example:
  - `Projects\Parabank`
- Prefer explicit `-p` project paths for `testar session start`.
- Do not silently reuse existing projects like `Projects\Tutorial` for a different application goal.
- If the intended project does not exist, create it explicitly before starting the TESTAR session.
- Preferred project creation command:
  - `ingenious-cli.bat project create Parabank -d Projects`

## BDD goal files

- Read scenario files from `.agents/bdd_goals/<application>/`.
- Prefer one scenario per file.
- Expect each file to start with `Scenario: ...` followed by `Given / When / And / Then` steps.
- Prefer `--bdd-goal <application/scenario_id>` at `testar session start`.
- Use `--bdd-file <path>` when the scenario is outside `.agents/bdd_goals`.
- Keep the scenario title and step text stable when passing `--bdd-scenario` and `--bdd-step`.
- When the user references a scenario by name, resolve it from `.agents/bdd_goals` before inventing new wording.

## Public TESTAR CLI commands

### Session lifecycle

- `ingenious-cli.bat testar session start -p <projectPath> --url <url> --bdd-goal <application/scenario_id>`
- `ingenious-cli.bat testar session start -p <projectPath> --url <url> --bdd-file <scenarioFilePath>`
- `ingenious-cli.bat testar session start -p <projectPath> --url <url> --bdd-scenario <scenarioName>`
- `ingenious-cli.bat testar session status`
- `ingenious-cli.bat testar session stop`

Required arguments:

- `<projectPath>`: INGenious project path relative to the packaged runtime root
- `<url>`: target web URL
- prefer `<application/scenario_id>` or `<scenarioFilePath>` so the daemon loads the full original BDD scenario
- `<scenarioName>` is only the fallback scenario title when no BDD goal file is provided

### Navigation

- `ingenious-cli.bat testar navigation url`
- `ingenious-cli.bat testar navigation back`

### State inspection

- `ingenious-cli.bat testar state widgets`
- `ingenious-cli.bat testar state text`
- `ingenious-cli.bat testar state image`

### Actions

- `ingenious-cli.bat testar action click --bdd-step <bddStep> --selector <cssSelector>`
- `ingenious-cli.bat testar action fill --bdd-step <bddStep> --selector <cssSelector> --text <fillText>`
- `ingenious-cli.bat testar action select --bdd-step <bddStep> --selector <cssSelector> --value <optionValue>`
- `ingenious-cli.bat testar action history`

### Assertions

- `ingenious-cli.bat testar assert add --bdd-step <bddStep> --text <assertText>`

## Parameter rules

- `<bddStep>` must stay stable and should match the intended generated scenario step text.
- `<cssSelector>` must come from the latest successful `testar state widgets` output.
- `<fillText>` is the literal text to type into the selected input.
- `<optionValue>` must match one of the option values exposed in the latest widget state for a `select` or `dropdown` element.
- `<assertText>` should be a visible, unique text grounded in the current page state.

## Selector semantics

For `testar action click`, `testar action fill`, and `testar action select`, `<cssSelector>` must come from the latest `testar state widgets` output.

Use the selector exactly as exposed by the latest widget state when possible.

## Selector and action rules

- Use `testar state widgets` before acting.
- Only execute actions against selectors visible in the latest state output.
- Use `testar action click` for links, buttons, and other clickable widgets.
- Keep `<bddStep>` stable across all commands that belong to the same generated scenario step.
- Keep selectors grounded in the latest observed state rather than guessing them.
- Re-check state after each action before deciding the next one.

## BDD order behavior

- The current CLI behavior matches the Studio behavior.
- A `bddStep` must belong to the loaded original BDD scenario.
- An already executed old non-latest step cannot be reused later.
- Continuing with the latest executed step is allowed.
- Jumping to a future not-yet-executed step is still allowed.
- This is partial order protection, not strict next-step-only enforcement.

## Evidence rules

- Treat `testar state widgets`, `testar state text`, and `testar state image` as execution evidence.
- Use `testar action history` to confirm what has already been executed in the active daemon session.
- `testar action history` is BDD-aware in the CLI path and shows which recorded actions belong to which `bddStep`.
- Prefer grounding assertions in `state text` output before creating them.
- `testar state image` now writes a PNG file under `tmp\testar\<sessionId>\...` and returns `imagePath`.

## Resilience and retry behavior rules

The target web application may need time to load screens, populate widgets, or reflect the result of an action.

When `testar state widgets`, `testar state text`, or `testar state image` fails, returns incomplete information, or does not yet reflect the expected UI change:

- Do not assume the goal failed immediately.
- Retry the same observation command after a short wait.
- Prefer refreshing `testar state widgets` before executing another action.
- Retry a small number of times before changing strategy.
- Keep retries sequential.
- Ground decisions in the latest successful state output.

## Daemon behavior

- The daemon starts automatically on the first TESTAR CLI command if it is not already running.
- `session stop` stops the active TESTAR session, but the daemon process may remain alive.
- The CLI prints:
  - `daemonPid`
  - `daemonActiveSession`
- `session status` may also print:
  - `scenarioTitle`
  - `scenarioSource`
  - `knownBddSteps`
  - `executedBddSteps`
  - `latestValidStep`
- If needed, the daemon process can be terminated manually using the printed PID.

## Artifact generation

Generated artifacts are written into the active INGenious project inside the packaged runtime, for example:

- `Projects\<project_name>\TestPlan\BDD-MCP_scenario`

Reusable components and supporting generated data are also persisted through the INGenious TESTAR services.

## Workflow

1. Create or choose a project first, for example `Projects\Parabank`.
2. If the project does not exist, create it explicitly with:
   - `ingenious-cli.bat project create Parabank -d Projects`
3. Load the BDD goal from `.agents\bdd_goals\...`.
4. Start one TESTAR session with `testar session start`.
5. Inspect the live UI with:
   - `testar state widgets`
   - `testar state text`
   - `testar state image`
6. Execute one action at a time.
7. Re-check state after each action.
8. Add assertions only after the target state is visible.
9. Stop the session with `testar session stop`.
10. Verify generated artifacts in the target INGenious project.

## Session example

- BDD scenario:
  - `Scenario: Requested loans with small down payments must be denied`
  - `Given the user navigates to the url 'https://para.testar.org/'`
  - `When the user logs in with the john/demo credentials`
  - `And the user navigates to request a loan`
  - `And the user fills out a big loan amount with a small down payment`
  - `And the user selects the account 13011 and applies for the loan`
  - `Then a message indicates the loan is denied`
- `ingenious-cli.bat testar session start -p "Projects\\Parabank" --url "https://para.testar.org/" --bdd-goal "parabank/requested_loans_with_small_down_payments_must_be_denied"`
- `ingenious-cli.bat testar state widgets`
- `ingenious-cli.bat testar action fill --bdd-step "When the user logs in with the john/demo credentials" --selector "[type='text']" --text "john"`
- `ingenious-cli.bat testar action fill --bdd-step "When the user logs in with the john/demo credentials" --selector "[type='password']" --text "demo"`
- `ingenious-cli.bat testar action click --bdd-step "When the user logs in with the john/demo credentials" --selector "[type='submit']"`
- `ingenious-cli.bat testar action click --bdd-step "And the user navigates to request a loan" --selector "a[href*='requestloan']"`
- `ingenious-cli.bat testar action fill --bdd-step "And the user fills out a big loan amount with a small down payment" --selector "#amount" --text "999999"`
- `ingenious-cli.bat testar action fill --bdd-step "And the user fills out a big loan amount with a small down payment" --selector "#downPayment" --text "100"`
- `ingenious-cli.bat testar action select --bdd-step "And the user selects the account 13011 and applies for the loan" --selector "#fromAccountId" --value "13011"`
- `ingenious-cli.bat testar action click --bdd-step "And the user selects the account 13011 and applies for the loan" --selector "input[value='Apply Now']"`
- `ingenious-cli.bat testar state text`
- `ingenious-cli.bat testar assert add --bdd-step "Then a message indicates the loan is denied" --text "Denied"`
- `ingenious-cli.bat testar session stop`

## Operational rules

- Use one stable session per goal when possible.
- Run commands sequentially.
- Treat `testar session start` and `testar session stop` as the public session lifecycle commands.
- Keep BDD step text stable when creating actions and assertions intended for reusable generated artifacts.
- Keep decisions grounded in the latest successful state output.
- If a command fails or state looks incomplete, retry observation commands before changing strategy.
