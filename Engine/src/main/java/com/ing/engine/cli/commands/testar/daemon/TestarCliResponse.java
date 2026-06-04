package com.ing.engine.cli.commands.testar.daemon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TestarCliResponse {

    private final int exitCode;
    private final List<String> lines;

    public TestarCliResponse(int exitCode, List<String> lines) {
        this.exitCode = exitCode;
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
    }

    public int getExitCode() {
        return exitCode;
    }

    public List<String> getLines() {
        return lines;
    }
}
