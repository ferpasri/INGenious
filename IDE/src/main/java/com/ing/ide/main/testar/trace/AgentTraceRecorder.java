package com.ing.ide.main.testar.trace;

import java.io.Closeable;
import java.io.IOException;

public interface AgentTraceRecorder extends Closeable {
    void append(AgentTraceEvent event) throws IOException;

    @Override
    void close() throws IOException;
}
