package com.ing.engine.cli.commands.testar.daemon;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class TestarCliDaemonClient {

    public TestarCliResponse send(TestarCliRequest request) {
        ensureDaemonRunning();
        IOException lastException = null;
        for (int attempt = 0; attempt < TestarCliDaemonConfig.MAX_REQUEST_ATTEMPTS; attempt++) {
            try {
                return sendOnce(request);
            } catch (IOException exception) {
                lastException = exception;
                sleepQuietly(TestarCliDaemonConfig.RETRY_DELAY_MS);
            }
        }

        throw new IllegalStateException("Unable to communicate with TESTAR CLI daemon", lastException);
    }

    private void ensureDaemonRunning() {
        if (isDaemonReachable()) {
            return;
        }

        startDaemonProcess();
        long deadline = System.currentTimeMillis() + TestarCliDaemonConfig.START_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (isDaemonReachable()) {
                return;
            }
            sleepQuietly(TestarCliDaemonConfig.RETRY_DELAY_MS);
        }

        throw new IllegalStateException("TESTAR CLI daemon did not start in time");
    }

    private boolean isDaemonReachable() {
        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(TestarCliDaemonConfig.HOST, TestarCliDaemonConfig.PORT),
                    200
            );
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private void startDaemonProcess() {
        String javaExecutable = resolveJavaExecutable();
        String classPath = System.getProperty("java.class.path");
        String appRoot = System.getProperty("user.dir");
        Path logFile = Path.of(appRoot, TestarCliDaemonConfig.LOG_FILE_NAME);

        ProcessBuilder builder = new ProcessBuilder(
                javaExecutable,
                "-Dingenious.testar.daemon=true",
                "-cp",
                classPath,
                "com.ing.engine.core.Control",
                "testar",
                "daemon"
        );
        builder.directory(new File(appRoot));
        builder.redirectErrorStream(true);
        builder.redirectOutput(logFile.toFile());

        try {
            builder.start();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start TESTAR CLI daemon: " + exception.getMessage(), exception);
        }
    }

    private String resolveJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("windows");
        String executableName = isWindows ? "java.exe" : "java";
        return Path.of(javaHome, "bin", executableName).toString();
    }

    private TestarCliResponse sendOnce(TestarCliRequest request) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(TestarCliDaemonConfig.HOST, TestarCliDaemonConfig.PORT),
                    TestarCliDaemonConfig.CONNECT_TIMEOUT_MS
            );

            try (DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                 DataInputStream input = new DataInputStream(socket.getInputStream())) {
                output.writeUTF(request.getCommand());
                output.writeInt(request.getArguments().size());
                for (String argument : request.getArguments()) {
                    output.writeUTF(argument != null ? argument : "");
                }
                output.flush();

                int exitCode = input.readInt();
                int lineCount = input.readInt();
                List<String> lines = new ArrayList<>(lineCount);
                for (int index = 0; index < lineCount; index++) {
                    lines.add(input.readUTF());
                }
                return new TestarCliResponse(exitCode, lines);
            }
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
