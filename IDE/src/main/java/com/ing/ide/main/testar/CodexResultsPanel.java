package com.ing.ide.main.testar;

import com.ing.ide.main.mainui.AppMainFrame;
import com.ing.ide.main.testar.codex.metrics.CodexMetricsDashboardWriter;
import com.ing.ide.util.Notification;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CodexResultsPanel {

    private static final Logger LOGGER = Logger.getLogger(CodexResultsPanel.class.getName());

    private final AppMainFrame sMainFrame;
    private final CodexMetricsDashboardWriter dashboardWriter = new CodexMetricsDashboardWriter();

    public CodexResultsPanel(AppMainFrame sMainFrame) {
        this.sMainFrame = sMainFrame;
    }

    public void openCodexResultsDashboard() {
        try {
            if (sMainFrame.getProject() == null || sMainFrame.getProject().getLocation() == null) {
                Notification.show("No active project available for Codex results.");
                return;
            }

            Path codexRoot = Paths.get(sMainFrame.getProject().getLocation(), "Results", "Codex");
            Path runsDir = codexRoot.resolve("runs");
            if (!Files.isDirectory(runsDir)) {
                Notification.show("No Codex runs available yet.");
                return;
            }

            dashboardWriter.writeDashboard(codexRoot);

            File dashboardFile = codexRoot.resolve("dashboard.html").toFile();
            if (!dashboardFile.exists()) {
                Notification.show("Failed to generate the Codex dashboard.");
                return;
            }

            if (!Desktop.isDesktopSupported()) {
                Notification.show("Desktop integration is not supported in this environment.");
                return;
            }

            Desktop.getDesktop().browse(dashboardFile.toURI());
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "Failed to open Codex results dashboard", exception);
            Notification.show("Failed to open Codex results dashboard.");
        }
    }
}
