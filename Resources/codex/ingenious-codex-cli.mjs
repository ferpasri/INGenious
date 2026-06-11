import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import readline from "node:readline/promises";
import { spawn } from "node:child_process";

function normalizeAnswer(answer, fallbackValue) {
    const value = answer == null ? "" : answer.trim();
    const normalized = value.replace(/^"(.*)"$/, "$1");
    return normalized === "" ? fallbackValue : normalized;
}

function sanitizeName(text) {
    return text.replace(/[\\\/?:*"|><]/g, "_");
}

function buildExecutionPrompt(userPrompt, projectPath) {
    return [
        "Codex execution context:",
        "- You are running from the packaged INGenious runtime root.",
        "- The current working directory already contains `ingenious-cli.bat` and `.agents`.",
        "- Resolve BDD goals from `.agents/bdd_goals`.",
        "- Pass INGenious project paths relative to the runtime root, for example `" + projectPath + "`.",
        "- Browser-based TESTAR runs need real local process access. Use an unsandboxed Codex run configuration for session start and browser automation steps.",
        "- On Windows, when invoking the packaged CLI launcher, prefer `cmd /c \"ingenious-cli.bat ...\"` instead of direct PowerShell execution of the `.bat` file.",
        "- Use the `ingenious-testar` skill instructions before driving TESTAR CLI commands.",
        "",
        userPrompt
    ].join("\n");
}

function timestamp() {
    const now = new Date();
    const pad = (value) => String(value).padStart(2, "0");
    return now.getFullYear()
            + "-" + pad(now.getMonth() + 1)
            + "-" + pad(now.getDate())
            + "_" + pad(now.getHours())
            + "-" + pad(now.getMinutes())
            + "-" + pad(now.getSeconds());
}

async function promptSettings() {
    const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout,
    });

    try {
        const projectPath = normalizeAnswer(
                await rl.question("Project path relative to release root [Projects\\Parabank]: "),
                "Projects\\Parabank"
        );
        const apiKeyEnvVarName = normalizeAnswer(
                await rl.question("API key env var [OPENAI_API_KEY]: "),
                "OPENAI_API_KEY"
        );
        const baseUrl = normalizeAnswer(
                await rl.question("Base URL [default OpenAI]: "),
                ""
        );
        const model = normalizeAnswer(
                await rl.question("Model [gpt-5.4-mini]: "),
                "gpt-5.4-mini"
        );
        const reasoningEffort = normalizeAnswer(
                await rl.question("Reasoning effort [medium]: "),
                "medium"
        );
        const sandboxMode = normalizeAnswer(
                await rl.question("Sandbox mode [danger-full-access]: "),
                "danger-full-access"
        );
        const approvalPolicy = normalizeAnswer(
                await rl.question("Approval policy [never]: "),
                "never"
        );
        const networkAccess = normalizeAnswer(
                await rl.question("Enable network access? [no]: "),
                "no"
        );
        const promptTitle = normalizeAnswer(
                await rl.question("Prompt title [Codex CLI Run]: "),
                "Codex CLI Run"
        );
        const prompt = normalizeAnswer(
                await rl.question("Prompt text: "),
                ""
        );

        return {
            projectPath,
            apiKeyEnvVarName,
            baseUrl,
            model,
            reasoningEffort,
            sandboxMode,
            approvalPolicy,
            networkAccessEnabled: /^y(es)?$/i.test(networkAccess),
            promptTitle,
            prompt,
        };
    } finally {
        rl.close();
    }
}

async function main() {
    const rawReleaseRoot = process.argv[2] ?? process.cwd();
    const releaseRoot = path.resolve(String(rawReleaseRoot).replace(/^"(.*)"$/, "$1"));
    const settings = await promptSettings();

    if (!settings.prompt) {
        throw new Error("Prompt text is required.");
    }

    const projectDirectory = path.resolve(releaseRoot, settings.projectPath);
    const runDirectory = path.resolve(
            projectDirectory,
            "Results",
            "Codex",
            "runs",
            sanitizeName(settings.promptTitle) + "_" + timestamp()
    );
    const requestPath = path.join(runDirectory, "request.json");

    await mkdir(runDirectory, { recursive: true });

    const request = {
        apiKeyEnvVarName: settings.apiKeyEnvVarName,
        baseUrl: settings.baseUrl,
        model: settings.model,
        reasoningEffort: settings.reasoningEffort,
        sandboxMode: settings.sandboxMode,
        approvalPolicy: settings.approvalPolicy,
        networkAccessEnabled: settings.networkAccessEnabled,
        skipGitRepoCheck: true,
        workingDirectory: releaseRoot,
        additionalDirectories: [releaseRoot, projectDirectory],
        prompt: buildExecutionPrompt(settings.prompt, settings.projectPath),
        outputDirectory: runDirectory,
    };

    await writeFile(requestPath, JSON.stringify(request, null, 4), "utf8");

    console.log("[CONTROL] Run folder: " + runDirectory);
    console.log("[CONTROL] Starting Codex CLI agent...");

    const child = spawn(
            process.execPath,
            [path.join(releaseRoot, "codex", "ingenious-codex-runner.mjs"), requestPath],
            {
                cwd: releaseRoot,
                stdio: "inherit",
            }
    );

    child.on("exit", (code) => {
        process.exitCode = code ?? 1;
    });
}

main().catch((error) => {
    console.error(error instanceof Error ? error.message : String(error));
    process.exitCode = 1;
});
