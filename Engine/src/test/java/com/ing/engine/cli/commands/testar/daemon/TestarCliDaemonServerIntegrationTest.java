package com.ing.engine.cli.commands.testar.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ing.ingenious.api.contract.testar.TestarBackendApi;
import com.ing.ingenious.api.contract.testar.TestarResult;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import org.testng.SkipException;
import org.testng.annotations.Test;

public class TestarCliDaemonServerIntegrationTest {

    @Test
    public void testSessionStopShutsDownRunningDaemonServer() throws Exception {
        if (isPortReachable()) {
            throw new SkipException("TESTAR CLI daemon port is already in use.");
        }

        TestarCliDaemonServer server = new TestarCliDaemonServer();
        TestarBackendApi backend = mock(TestarBackendApi.class);
        when(backend.stopSession()).thenReturn(TestarResult.success("stopped", "TESTAR session stopped.", null));
        setPrivateField(server, "backend", backend);

        Thread serverThread = new Thread(server::run, "testar-cli-daemon-test");
        serverThread.setDaemon(true);
        serverThread.start();

        waitUntilReachable();

        TestarCliResponse response = sendRequest(TestarCliRequest.of("session.stop", List.of()));

        assertThat(response.getExitCode()).isZero();
        assertThat(response.getLines()).contains("status=stopped");
        assertThat(response.getLines()).contains("daemonActiveSession=false");

        serverThread.join(3000L);

        verify(backend, times(1)).stopSession();
        assertThat(serverThread.isAlive()).isFalse();
        assertThat(isPortReachable()).isFalse();
    }

    private TestarCliResponse sendRequest(TestarCliRequest request) throws IOException {
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

    private void waitUntilReachable() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000L;
        while (System.currentTimeMillis() < deadline) {
            if (isPortReachable()) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("TESTAR CLI daemon server did not start in time.");
    }

    private boolean isPortReachable() {
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

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
