import assert from "node:assert/strict";
import path from "node:path";
import test from "node:test";
import { resolveRunPaths } from "../../../../Resources/codex/ingenious-codex-paths.mjs";

test("portable run markers resolve after the project is moved", () => {
    const releaseRoot = path.resolve("new-location", "release");
    const projectDirectory = path.join(releaseRoot, "Projects", "Parabank");
    const requestPath = path.join(
        projectDirectory, "Results", "Codex", "runs", "loan-run", "request.json"
    );
    const paths = resolveRunPaths({
        workingDirectory: "@release",
        outputDirectory: "@run",
        additionalDirectories: ["@release", "@project"],
    }, requestPath, releaseRoot);

    assert.equal(paths.workingDirectory, releaseRoot);
    assert.equal(paths.outputDirectory, path.dirname(requestPath));
    assert.deepEqual(paths.additionalDirectories, [releaseRoot, projectDirectory]);
});

test("legacy absolute run paths remain readable", () => {
    const releaseRoot = path.resolve("original-location", "release");
    const requestPath = path.join(releaseRoot, "Projects", "Parabank", "Results",
        "Codex", "runs", "loan-run", "request.json");
    const paths = resolveRunPaths({
        workingDirectory: releaseRoot,
        outputDirectory: path.dirname(requestPath),
        additionalDirectories: [releaseRoot],
    }, requestPath, path.resolve("new-location", "release"));

    assert.equal(paths.workingDirectory, releaseRoot);
    assert.equal(paths.outputDirectory, path.dirname(requestPath));
});
