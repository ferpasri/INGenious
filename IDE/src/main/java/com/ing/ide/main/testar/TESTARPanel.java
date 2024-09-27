package com.ing.ide.main.testar;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import com.ing.ide.main.mainui.AppMainFrame;
import com.ing.ide.settings.IconSettings;

public class TESTARPanel {

	private final AppMainFrame sMainFrame;

	public TESTARPanel(AppMainFrame sMainFrame) {
		this.sMainFrame = sMainFrame;
	}

	// TODO: Replace this with TESTAR dialog
	public void openEditor() {
		// Create a modal dialog
		JDialog dialog = new JDialog(sMainFrame, "TESTAR Scriptless Testing", true);
		dialog.setSize(400, 600);
		dialog.setLayout(new BorderLayout());
		dialog.add(new JLabel(IconSettings.getIconSettings().getTESTARIcon()), BorderLayout.NORTH);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		// Create a panel for the input components
		JPanel inputPanel = new JPanel();
		inputPanel.setLayout(new GridLayout(5, 2, 10, 10));

		// Add label and text field for URL input
		JLabel urlLabel = new JLabel("Enter URL:");
		JTextField urlTextField = new JTextField(40);
		urlTextField.setText("https://para.testar.org/");
		inputPanel.add(urlLabel);
		inputPanel.add(urlTextField);

		// Add label and text field for number of actions
		JLabel actionsLabel = new JLabel("Number of Actions:");
		JSpinner actionsSpinner = new JSpinner();
		actionsSpinner.setValue(10);
		inputPanel.add(actionsLabel);
		inputPanel.add(actionsSpinner);

		// Add label and text field for the regex used for filtering actions
		JLabel filterLabel = new JLabel("Regex filtering:");
		JTextField filterTextField = new JTextField(40);
		filterTextField.setText(".*([wW]sdl|[wW]adl|[xX]ml|[pP]arasoft).*");
		inputPanel.add(filterLabel);
		inputPanel.add(filterTextField);

		// Add label and text field for the regex used for detecting suspicious messages
		JLabel suspiciousLabel = new JLabel("Regex Suspicious:");
		JTextField suspiciousTextField = new JTextField(40);
		suspiciousTextField.setText(".*([eE]xcepti[o?]n).*");
		inputPanel.add(suspiciousLabel);
		inputPanel.add(suspiciousTextField);

		// Text area to display final verdict
		JLabel verdictLabel = new JLabel("Verdict:");
		JTextArea verdictArea = new JTextArea(1, 40);
		verdictArea.setEditable(false); // Read-only area
		verdictArea.setLineWrap(true);
		verdictArea.setWrapStyleWord(true);
		inputPanel.add(verdictLabel);
		inputPanel.add(verdictArea);

		// Create a panel for buttons
		JPanel buttonPanel = new JPanel();
		JButton launchButton = new JButton("Launch");
		JButton closeButton = new JButton("Close");

		// Launch button action
		launchButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String url = urlTextField.getText();
				int numberActions = (Integer) actionsSpinner.getValue();
				String filterPattern = filterTextField.getText();
				String suspiciousPattern = suspiciousTextField.getText();

				TESTARtool testar = new TESTARtool(url, numberActions, filterPattern, suspiciousPattern);

				// Create a SwingWorker to execute the long-running task
				SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
					@Override
					protected String doInBackground() throws Exception {
						return testar.generateSequence(); // Long-running task
					}

					@Override
					protected void done() {
						try {
							// Retrieve the result and update the UI
							String finalVerdict = get();
							verdictArea.setText(finalVerdict);
						} catch (Exception ex) {
							ex.printStackTrace();
							verdictArea.setText("Error: " + ex.getMessage());
						}
					}
				};

				// Start the background task
				worker.execute();
			}
		});

		// Close button action
		closeButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				dialog.dispose(); // Close the dialog without any action
			}
		});

		buttonPanel.add(launchButton);
		buttonPanel.add(closeButton);

		// Add panels to dialog
		dialog.add(inputPanel, BorderLayout.CENTER);
		dialog.add(buttonPanel, BorderLayout.SOUTH);

		// Set dialog visible
		dialog.setLocationRelativeTo(sMainFrame); // Center the dialog relative to the main frame
		dialog.setVisible(true);
	}
}
