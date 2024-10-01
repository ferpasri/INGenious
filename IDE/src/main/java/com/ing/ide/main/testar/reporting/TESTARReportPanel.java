package com.ing.ide.main.testar.reporting;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ing.ide.main.mainui.AppMainFrame;
import com.ing.ide.main.utils.Utils;
import com.ing.ide.settings.IconSettings;

public class TESTARReportPanel {
	private static final Logger logger = LogManager.getLogger();

	private final AppMainFrame sMainFrame;
	private String reportsPath;

	public TESTARReportPanel(AppMainFrame sMainFrame) {
		this.sMainFrame = sMainFrame;

		try {
			reportsPath = Utils.getAppRoot() + File.separator;
		} catch (IOException e) {
			logger.log(Level.ERROR, e.getMessage());
			reportsPath = System.getProperty("user.dir") + File.separator;
			logger.log(Level.ERROR, "TESTARReportPanel reading HTML reports from: " + reportsPath);
		}
	}

	public void openEditor() {
		// Create a modal dialog
		JDialog dialog = new JDialog(sMainFrame, "TESTAR HTML Reports", true);
		dialog.setSize(800, 600);
		dialog.setLayout(new BorderLayout());
		dialog.add(new JLabel(IconSettings.getIconSettings().getTESTARIcon()), BorderLayout.NORTH);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		// Create a panel to hold the report list or message
		JPanel reportPanel = new JPanel();
		reportPanel.setLayout(new BorderLayout());

		// List all HTML files from the specified location
		File reportDirectory = new File(reportsPath);
		File[] htmlFiles = reportDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".html"));

		if (htmlFiles != null && htmlFiles.length > 0) {
			// Create a JList to display the HTML files
			String[] reportNames = Arrays.stream(htmlFiles)
					.map(File::getName)
					.toArray(String[]::new);

			JList<String> reportList = new JList<>(reportNames);
			reportList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

			// Add a mouse listener to detect clicks on list items
			reportList.addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					if (e.getClickCount() == 2) { // Double-click to open
						int index = reportList.locationToIndex(e.getPoint());
						String selectedFile = reportNames[index];
						File fileToOpen = new File(reportsPath, selectedFile);
						openFileInBrowser(fileToOpen);
					}
				}
			});

			// Add a scroll pane to make the list scrollable if there are many reports
			JScrollPane scrollPane = new JScrollPane(reportList);
			reportPanel.add(scrollPane, BorderLayout.CENTER);
		} else {
			// If no HTML reports are found, show a message
			JLabel noReportsLabel = new JLabel("No HTML reports found in the specified location.");
			noReportsLabel.setHorizontalAlignment(SwingConstants.CENTER);
			reportPanel.add(noReportsLabel, BorderLayout.CENTER);
		}

		// Add the report panel to the dialog
		dialog.add(reportPanel, BorderLayout.CENTER);

		// Set dialog visible
		dialog.setLocationRelativeTo(sMainFrame); // Center the dialog relative to the main frame
		dialog.setVisible(true);
	}

	private void openFileInBrowser(File file) {
		try {
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				// Open the file using the system's default browser
				Desktop.getDesktop().browse(file.toURI());
			} else {
				JOptionPane.showMessageDialog(sMainFrame, "Opening in a browser is not supported on your system.", "Error", JOptionPane.ERROR_MESSAGE);
			}
		} catch (IOException e) {
			JOptionPane.showMessageDialog(sMainFrame, "Failed to open the file in browser: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}
	}
}
