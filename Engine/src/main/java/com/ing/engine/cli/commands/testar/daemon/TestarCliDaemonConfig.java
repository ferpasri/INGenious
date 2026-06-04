package com.ing.engine.cli.commands.testar.daemon;

final class TestarCliDaemonConfig {

    static final String HOST = "127.0.0.1";
    static final int PORT = 47328;
    static final int START_TIMEOUT_MS = 15000;
    static final int CONNECT_TIMEOUT_MS = 1000;
    static final int MAX_REQUEST_ATTEMPTS = 5;
    static final long RETRY_DELAY_MS = 200L;
    static final String LOG_FILE_NAME = "testar-cli-daemon.log";

    private TestarCliDaemonConfig() {
    }
}
