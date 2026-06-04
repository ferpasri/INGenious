package com.ing.engine.cli.commands.testar.daemon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TestarCliRequest {

    private final String command;
    private final List<String> arguments;

    private TestarCliRequest(String command, List<String> arguments) {
        this.command = command;
        this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
    }

    public static TestarCliRequest of(String command, List<String> arguments) {
        return new TestarCliRequest(command, arguments);
    }

    public String getCommand() {
        return command;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public String argumentAt(int index) {
        return index >= 0 && index < arguments.size() ? arguments.get(index) : null;
    }
}
