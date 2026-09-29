package com.ing.ide.main.testar.trace;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class JsonlAgentTraceRecorder implements AgentTraceRecorder {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final BufferedWriter writer;
    private boolean closed;

    public JsonlAgentTraceRecorder(Path traceFile) throws IOException {
        Path parent = traceFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        writer =
            Files.newBufferedWriter(
                traceFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
                StandardOpenOption.WRITE
            );
    }

    @Override
    public synchronized void append(AgentTraceEvent event) throws IOException {
        if (closed) {
            throw new IOException("Trace recorder is closed.");
        }
        writer.write(JSON_MAPPER.writeValueAsString(event));
        writer.newLine();
        writer.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        writer.close();
    }
}
