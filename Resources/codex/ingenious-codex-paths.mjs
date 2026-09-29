import path from "node:path";

export function resolveRunPaths(config, requestPath, releaseRoot) {
    const runDirectory = path.dirname(path.resolve(requestPath));
    const projectDirectory = path.resolve(runDirectory, "../../../..");
    const roots = {
        "@release": path.resolve(releaseRoot),
        "@project": projectDirectory,
        "@run": runDirectory,
    };

    function resolve(value, fallback) {
        if (!value) {
            return fallback;
        }
        if (Object.hasOwn(roots, value)) {
            return roots[value];
        }
        return path.resolve(runDirectory, value);
    }

    return {
        outputDirectory: resolve(config.outputDirectory, runDirectory),
        workingDirectory: resolve(config.workingDirectory, roots["@release"]),
        additionalDirectories: Array.isArray(config.additionalDirectories)
            ? config.additionalDirectories.map(value => resolve(value, "")).filter(Boolean)
            : [],
    };
}
