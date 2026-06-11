package com.ing.engine.cli.commands.testar.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ing.ingenious.api.contract.testar.TestarBackendApi;
import com.ing.ingenious.api.contract.testar.TestarResult;
import java.lang.reflect.Field;
import java.util.List;
import org.testng.annotations.Test;

public class TestarCliDaemonServerTest {

    @Test
    public void testHandleSessionStatusWithoutBackend() {
        TestarCliDaemonServer server = new TestarCliDaemonServer();

        TestarCliResponse response = server.handle(TestarCliRequest.of("session.status", List.of()));

        assertThat(response.getExitCode()).isZero();
        assertThat(response.getLines()).contains("status=idle");
        assertThat(response.getLines()).contains("daemonActiveSession=false");
    }

    @Test
    public void testHandleSessionStopMarksDaemonForShutdown() throws Exception {
        TestarCliDaemonServer server = new TestarCliDaemonServer();
        TestarBackendApi backend = mock(TestarBackendApi.class);
        when(backend.stopSession()).thenReturn(TestarResult.success("stopped", "TESTAR session stopped.", null));
        setPrivateField(server, "backend", backend);

        TestarCliResponse response = server.handle(TestarCliRequest.of("session.stop", List.of()));

        verify(backend, times(1)).stopSession();
        assertThat(response.getExitCode()).isZero();
        assertThat(response.getLines()).contains("success=true");
        assertThat(response.getLines()).contains("status=stopped");
        assertThat(response.getLines()).contains("message=TESTAR session stopped.");
        assertThat(response.getLines()).contains("daemonActiveSession=false");
        assertThat(getPrivateField(server, "backend")).isNull();
        assertThat((Boolean) getPrivateField(server, "shutdownRequested")).isTrue();
    }

    @Test
    public void testHandleUnknownCommandReturnsErrorResponse() {
        TestarCliDaemonServer server = new TestarCliDaemonServer();

        TestarCliResponse response = server.handle(TestarCliRequest.of("unknown.command", List.of()));

        assertThat(response.getExitCode()).isEqualTo(1);
        assertThat(response.getLines()).contains("status=error");
        assertThat(response.getLines()).anyMatch(line -> line.contains("Unknown TESTAR daemon command"));
        assertThat(response.getLines()).contains("daemonActiveSession=false");
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Object getPrivateField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
