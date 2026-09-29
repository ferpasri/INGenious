package com.ing.ide.main.testar.trace;

import java.io.IOException;

public class NoOpAgentTraceRecorder implements AgentTraceRecorder {

    @Override
    public void append(AgentTraceEvent event) throws IOException {}

    @Override
    public void close() throws IOException {}
}
