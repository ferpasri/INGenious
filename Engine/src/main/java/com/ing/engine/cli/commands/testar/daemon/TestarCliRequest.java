package com.ing.engine.cli.commands.testar.daemon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TestarCliRequest {
    private final String command;
    private final List<String> arguments;
    private final String traceRunId;
    private final String traceDirectory;

    private TestarCliRequest(
        String command,
        List<String> arguments,
        String traceRunId,
        String traceDirectory
    ) {
        this.command = command;
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
        this.traceRunId = traceRunId != null ? traceRunId : "";
        this.traceDirectory = traceDirectory != null ? traceDirectory : "";
    }

    public static TestarCliRequest of(String command, List<String> arguments) {
        return new TestarCliRequest(command, arguments, "", "");
    }

    public static TestarCliRequest of(
        String command,
        List<String> arguments,
        String traceRunId,
        String traceDirectory
    ) {
        return new TestarCliRequest(command, arguments, traceRunId, traceDirectory);
    }

    public String getCommand() {
        return command;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public String getTraceRunId() {
        return traceRunId;
    }

    public String getTraceDirectory() {
        return traceDirectory;
    }

    public String argumentAt(int index) {
        return index >= 0 && index < arguments.size() ? arguments.get(index) : null;
    }
}
