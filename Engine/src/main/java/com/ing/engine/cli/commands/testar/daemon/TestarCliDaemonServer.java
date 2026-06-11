package com.ing.engine.cli.commands.testar.daemon;

import com.ing.engine.cli.commands.testar.TestarBackendLoader;
import com.ing.ingenious.api.contract.testar.TestarBackendApi;
import com.ing.ingenious.api.contract.testar.TestarResult;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public final class TestarCliDaemonServer {

    private TestarBackendApi backend;
    private final long daemonPid = ProcessHandle.current().pid();
    private volatile boolean running = true;
    private volatile boolean shutdownRequested;
    private ServerSocket serverSocket;

    public void run() {
        try (ServerSocket boundServerSocket = new ServerSocket(TestarCliDaemonConfig.PORT, 50)) {
            this.serverSocket = boundServerSocket;
            Runtime.getRuntime().addShutdownHook(new Thread(this::stopBackendQuietly));
            while (running) {
                try (Socket socket = serverSocket.accept()) {
                    handle(socket);
                } catch (EOFException exception) {
                    // Ignore empty reachability probes.
                } catch (IOException exception) {
                    if (!running) {
                        break;
                    }
                    // Keep daemon alive for subsequent requests.
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to start TESTAR CLI daemon server", exception);
        } finally {
            serverSocket = null;
        }
    }

    private void handle(Socket socket) throws IOException {
        try (DataInputStream input = new DataInputStream(socket.getInputStream());
             DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
            String command = input.readUTF();
            int argumentCount = input.readInt();
            List<String> arguments = new ArrayList<>(argumentCount);
            for (int index = 0; index < argumentCount; index++) {
                arguments.add(input.readUTF());
            }

            TestarCliResponse response = handle(TestarCliRequest.of(command, arguments));
            output.writeInt(response.getExitCode());
            output.writeInt(response.getLines().size());
            for (String line : response.getLines()) {
                output.writeUTF(line);
            }
            output.flush();
        }

        shutdownIfRequested();
    }

    synchronized TestarCliResponse handle(TestarCliRequest request) {
        String command = request.getCommand();
        try {
            if ("session.start".equals(command)) {
                return startSession(request);
            }
            if ("session.status".equals(command)) {
                return sessionStatus();
            }
            if ("session.stop".equals(command)) {
                TestarBackendApi loadedBackend = requireBackend();
                TestarCliResponse response = wrapResult(loadedBackend.stopSession());
                backend = null;
                shutdownRequested = true;
                return withDaemonMetadata(response, false);
            }
            if ("navigation.url".equals(command)) {
                return wrapResult(requireBackend().getCurrentUrl());
            }
            if ("navigation.back".equals(command)) {
                return wrapResult(requireBackend().navigateBack());
            }
            if ("state.widgets".equals(command)) {
                return wrapResult(requireBackend().getStateInteractiveWidgets());
            }
            if ("state.text".equals(command)) {
                return wrapResult(requireBackend().getStateVisualText());
            }
            if ("state.image".equals(command)) {
                return wrapResult(requireBackend().getStateImage());
            }
            if ("action.click".equals(command)) {
                return wrapResult(requireBackend().executeClickAction(request.argumentAt(0), request.argumentAt(1)));
            }
            if ("action.fill".equals(command)) {
                return wrapResult(requireBackend().executeFillAction(request.argumentAt(0), request.argumentAt(1), request.argumentAt(2)));
            }
            if ("action.select".equals(command)) {
                return wrapResult(requireBackend().executeSelectAction(request.argumentAt(0), request.argumentAt(1), request.argumentAt(2)));
            }
            if ("action.history".equals(command)) {
                return wrapResult(requireBackend().getExecutedActions());
            }
            if ("assert.add".equals(command)) {
                return wrapResult(requireBackend().addAssert(request.argumentAt(0), request.argumentAt(1)));
            }
            if ("daemon.ping".equals(command)) {
                return withDaemonMetadata(new TestarCliResponse(0, List.of("status=ready")), backend != null);
            }
        } catch (RuntimeException exception) {
            return withDaemonMetadata(new TestarCliResponse(1, List.of(
                    "status=error",
                    "message=" + sanitize(exception.getMessage() != null ? exception.getMessage() : "Unknown daemon error.")
            )), backend != null);
        }

        return withDaemonMetadata(
                new TestarCliResponse(1, List.of("status=error", "message=Unknown TESTAR daemon command: " + command)),
                backend != null
        );
    }

    private TestarCliResponse startSession(TestarCliRequest request) {
        String projectPath = request.argumentAt(0);
        String bddScenarioName = request.argumentAt(1);
        String bddInstructions = request.getArguments().size() >= 5 ? request.argumentAt(2) : (request.getArguments().size() >= 4 ? request.argumentAt(2) : "");
        String bddScenarioSource = request.getArguments().size() >= 5 ? request.argumentAt(3) : "";
        String url = request.getArguments().size() >= 5
                ? request.argumentAt(4)
                : (request.getArguments().size() >= 4 ? request.argumentAt(3) : request.argumentAt(2));

        if (projectPath == null || projectPath.trim().isEmpty()) {
            return new TestarCliResponse(1, List.of("status=error", "message=Project path required."));
        }
        if (url == null || url.trim().isEmpty()) {
            return new TestarCliResponse(1, List.of("status=error", "message=Session URL required."));
        }

        stopBackendQuietly();
        backend = TestarBackendLoader.load();
        if (backend == null) {
            return new TestarCliResponse(1, List.of("status=error", "message=No TESTAR automation backend available on the classpath."));
        }

        return withDaemonMetadata(wrapResult(backend.startSession(projectPath, bddScenarioName, bddInstructions, bddScenarioSource, url)), true);
    }

    private TestarCliResponse sessionStatus() {
        if (backend == null) {
            return withDaemonMetadata(
                    new TestarCliResponse(0, List.of(
                            "status=idle",
                            "message=TESTAR daemon is running with no active session."
                    )),
                    false
            );
        }

        return withDaemonMetadata(wrapResult(backend.getSessionStatus()), true);
    }

    private TestarBackendApi requireBackend() {
        if (backend == null) {
            throw new IllegalStateException("No active TESTAR daemon session. Start one with 'ingenious testar session start'.");
        }
        return backend;
    }

    private TestarCliResponse wrapResult(TestarResult result) {
        if (result == null) {
            return new TestarCliResponse(1, List.of("status=error", "message=No TESTAR backend result available."));
        }

        List<String> lines = new ArrayList<>();
        lines.add("success=" + result.isSuccess());
        lines.add("status=" + sanitize(result.getStatus()));
        if (result.getMessage() != null && !result.getMessage().isEmpty()) {
            lines.add("message=" + sanitize(result.getMessage()));
        }
        if (result.getPayload() != null && !result.getPayload().isEmpty() && !result.getPayload().equals(result.getMessage())) {
            lines.add("payload=" + sanitize(result.getPayload()));
        }
        return new TestarCliResponse(result.isSuccess() ? 0 : 1, lines);
    }

    private void stopBackendQuietly() {
        if (backend == null) {
            return;
        }

        try {
            backend.stopSession();
        } catch (RuntimeException exception) {
            // Ignore shutdown failures during replacement/shutdown.
        } finally {
            backend = null;
        }
    }

    private void shutdownIfRequested() {
        if (!shutdownRequested) {
            return;
        }

        running = false;
        ServerSocket socket = serverSocket;
        if (socket == null || socket.isClosed()) {
            return;
        }

        try {
            socket.close();
        } catch (IOException exception) {
            // Ignore close failures during daemon shutdown.
        }
    }

    private String sanitize(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n");
    }

    private TestarCliResponse withDaemonMetadata(TestarCliResponse response, boolean activeSession) {
        List<String> lines = new ArrayList<>(response.getLines());
        lines.add("daemonPid=" + daemonPid);
        lines.add("daemonActiveSession=" + activeSession);
        return new TestarCliResponse(response.getExitCode(), lines);
    }
}
