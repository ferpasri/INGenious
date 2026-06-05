package com.ing.ide.main.testar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ing.ide.main.mainui.AppMainFrame;
import com.ing.ide.main.mainui.components.testdesign.tree.model.ReusableTreeModel;
import com.ing.ide.main.testar.mcp.LlmMcpAgent;
import com.ing.ide.main.testar.mcp.McpAgentSettings;
import com.ing.ide.main.testar.mcp.provider.LlmProviderFactory;
import com.ing.ide.settings.IconSettings;
import com.ing.ide.util.Notification;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MCPAgentPanel {

	private final AppMainFrame sMainFrame;

	private static final Path SETTINGS_PATH = Paths.get(System.getProperty("user.home"), ".ingenious-mcp-settings.json");
	private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

	private String defaultBDDName = "User should be able to log in to Parabank";

	private String defaultBDDInstructions =
			"Given the user navigates to the url 'https://para.testar.org/'\n" +
			"When the user logs in with the john/demo credentials\n" +
			"Then a welcome john smith message is shown";

	public MCPAgentPanel(AppMainFrame sMainFrame) {
		this.sMainFrame = sMainFrame;
	}

	public void openEditor() {
		// load persisted settings
		McpAgentSettings settings = loadSettings();

		// Create a modal dialog
		JDialog dialog = new JDialog(sMainFrame, "TESTAR MCP Scriptless agent", true);
		dialog.setSize(500, 600);
		dialog.setLayout(new BorderLayout());
		dialog.add(new JLabel(IconSettings.getIconSettings().getTESTARIcon()), BorderLayout.NORTH);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		// Create a panel for the input components
		JPanel inputPanel = new JPanel();
		inputPanel.setLayout(new BorderLayout());

		JPanel formPanel = new JPanel(new GridLayout(8, 2, 5, 5));
		String[] providerModels = getProviderModels(LlmProviderFactory.normalizeProviderName(settings.llmProviderName));

		JLabel providerLabel = new JLabel("LLM Provider:");
		JComboBox<String> providerCombo = new JComboBox<>(LlmProviderFactory.supportedProviders());
		String providerDefault = LlmProviderFactory.normalizeProviderName(settings.llmProviderName);
		providerCombo.setSelectedItem(providerDefault);
		formPanel.add(providerLabel);
		formPanel.add(providerCombo);

		JLabel apiUrlLabel = new JLabel("API URL:");
		JTextField apiUrlField = new JTextField(100);
		String apiUrlDefault = settings.apiUrl != null
				? settings.apiUrl
				: LlmProviderFactory.defaultApiUrlFor(providerDefault);
		apiUrlField.setText(apiUrlDefault);
		formPanel.add(apiUrlLabel);
		formPanel.add(apiUrlField);

		JLabel apiKeyEnvVarLabel = new JLabel("API key env var:");
		JTextField apiKeyEnvVarField = new JTextField(40);
		String apiEnvDefault = settings.apiKeyEnvVarName != null
				? settings.apiKeyEnvVarName
				: LlmProviderFactory.defaultApiKeyEnvVarFor(providerDefault);
		apiKeyEnvVarField.setText(apiEnvDefault);
		formPanel.add(apiKeyEnvVarLabel);
		formPanel.add(apiKeyEnvVarField);

		JLabel openaiLabel = new JLabel("Model:");
		JComboBox<String> modelCombo = new JComboBox<>(providerModels);
		modelCombo.setEditable(true);
		String modelDefault = settings.openaiModel != null
				? settings.openaiModel
				: LlmProviderFactory.defaultModelFor(providerDefault);
		formPanel.add(openaiLabel);
		formPanel.add(modelCombo);
		modelCombo.setSelectedItem(modelDefault);

		JLabel visionLabel = new JLabel("Vision:");
		JCheckBox visionCheckBox = new JCheckBox("Enable vision");
		boolean visionDefault = settings.vision != null ? settings.vision : false;
		visionCheckBox.setSelected(visionDefault);
		formPanel.add(visionLabel);
		formPanel.add(visionCheckBox);

		JLabel reasoningLabel = new JLabel("Reasoning effort:");
		String[] reasoningOptions = { "none", "low", "medium", "high" };
		JComboBox<String> reasoningCombo = new JComboBox<>(reasoningOptions);
		String reasoningDefault = settings.reasoningLevel != null ? settings.reasoningLevel : "none";
		reasoningCombo.setSelectedItem(reasoningDefault);
		formPanel.add(reasoningLabel);
		formPanel.add(reasoningCombo);

		JLabel actionsLabel = new JLabel("Max Actions:");
		JSpinner actionsSpinner = new JSpinner();
		int actionsDefault = settings.maxActions != null ? settings.maxActions : 10;
		actionsSpinner.setValue(actionsDefault);
		formPanel.add(actionsLabel);
		formPanel.add(actionsSpinner);

		// Add a BDD Scenario name
		JLabel bddScenarioNameLabel = new JLabel("BDD Scenario Name:");
		JTextField bddScenarioNameField = new JTextField(40);
		String bddScenarioNameDefault = settings.bddScenarioName != null ? settings.bddScenarioName : defaultBDDName;
		bddScenarioNameField.setText(bddScenarioNameDefault);
		formPanel.add(bddScenarioNameLabel);
		formPanel.add(bddScenarioNameField);

		JToggleButton advancedToggle = new JToggleButton("Advanced");
		JPanel advancedPanel = new JPanel(new GridLayout(1, 2, 5, 5));
		advancedPanel.setVisible(false);
		JLabel numRunsLabel = new JLabel("Num Runs (batch):");
		JSpinner numRunsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 50, 1));
		int numRunsDefault = settings.numRuns != null ? settings.numRuns : 1;
		numRunsSpinner.setValue(numRunsDefault);
		advancedPanel.add(numRunsLabel);
		advancedPanel.add(numRunsSpinner);

		providerCombo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				String selectedProvider = (String) providerCombo.getSelectedItem();
				if (selectedProvider == null) {
					return;
				}

				Object currentSelection = modelCombo.getSelectedItem();
				modelCombo.removeAllItems();
				for (String providerModel : getProviderModels(selectedProvider)) {
					modelCombo.addItem(providerModel);
				}

				apiUrlField.setText(LlmProviderFactory.defaultApiUrlFor(selectedProvider));
				apiKeyEnvVarField.setText(LlmProviderFactory.defaultApiKeyEnvVarFor(selectedProvider));
				if (currentSelection == null || currentSelection.toString().isBlank()) {
					modelCombo.setSelectedItem(LlmProviderFactory.defaultModelFor(selectedProvider));
				} else {
					modelCombo.setSelectedItem(currentSelection.toString());
				}
			}
		});

		advancedToggle.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent event) {
				boolean expanded = advancedToggle.isSelected();
				advancedPanel.setVisible(expanded);
				advancedToggle.setText(expanded ? "Advanced \u25BE" : "Advanced");
				dialog.pack();
				dialog.setLocationRelativeTo(sMainFrame);
			}
		});

		JPanel topPanel = new JPanel(new BorderLayout(0, 5));
		topPanel.add(formPanel, BorderLayout.NORTH);

		JPanel advancedSection = new JPanel(new BorderLayout(0, 5));
		advancedSection.add(advancedToggle, BorderLayout.NORTH);
		advancedSection.add(advancedPanel, BorderLayout.CENTER);
		topPanel.add(advancedSection, BorderLayout.SOUTH);

		inputPanel.add(topPanel, BorderLayout.NORTH);

		// Add a BDD Instructions text area with scroll
		JLabel bddLabel = new JLabel("BDD Instructions:");
		String bddDefault = settings.bddInstructions != null ? settings.bddInstructions : defaultBDDInstructions;
		JTextArea bddInstructionsTextArea = new JTextArea(bddDefault, 10, 40);
		bddInstructionsTextArea.setLineWrap(true);
		bddInstructionsTextArea.setWrapStyleWord(true);
		JScrollPane bddScrollPane = new JScrollPane(bddInstructionsTextArea);

		JPanel bddPanel = new JPanel(new BorderLayout());
		bddPanel.add(bddLabel, BorderLayout.NORTH);
		bddPanel.add(bddScrollPane, BorderLayout.CENTER);

		inputPanel.add(bddPanel, BorderLayout.CENTER);

		// Create a panel for buttons
		JPanel buttonPanel = new JPanel();
		JButton launchButton = new JButton("Launch");
		JButton closeButton = new JButton("Save/Close");

		Runnable saveFromUi = () -> {
			settings.llmProviderName = (String) providerCombo.getSelectedItem();
			settings.apiUrl = apiUrlField.getText().trim();
			settings.apiKeyEnvVarName = apiKeyEnvVarField.getText().trim();
			Object selectedModel = modelCombo.getSelectedItem();
			settings.openaiModel = selectedModel != null ? selectedModel.toString().trim() : "";
			settings.vision = visionCheckBox.isSelected();
			settings.reasoningLevel = (String) reasoningCombo.getSelectedItem();
			settings.maxActions = (Integer) actionsSpinner.getValue();
			settings.numRuns = (Integer) numRunsSpinner.getValue();
			settings.bddScenarioName = bddScenarioNameField.getText().trim();
			settings.bddInstructions = normalizeBddInstructions(bddInstructionsTextArea.getText());
			bddInstructionsTextArea.setText(settings.bddInstructions);

			// keep in-memory default in sync as well
			defaultBDDName = settings.bddScenarioName;
			defaultBDDInstructions = settings.bddInstructions;

			saveSettings(settings);
		};

		launchButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				saveFromUi.run();

				// Disable interaction with the dialog panel elements
				setDialogBusy(dialog, true);

				SwingWorker<String, Void> worker = new SwingWorker<String, Void>() {
					@Override
					protected String doInBackground() throws Exception {
						int totalRuns = (Integer) numRunsSpinner.getValue();
						String lastResult = "";

						for (int runIndex = 1; runIndex <= totalRuns; runIndex++) {
							final int currentRun = runIndex;
							SwingUtilities.invokeLater(() ->
									launchButton.setText(totalRuns > 1
											? "Launch " + currentRun + "/" + totalRuns
											: "Launch"));

							McpAgentSettings launchSettings = new McpAgentSettings();
							launchSettings.llmProviderName = (String) providerCombo.getSelectedItem();
							launchSettings.apiKeyEnvVarName = apiKeyEnvVarField.getText().trim();
							launchSettings.apiUrl = apiUrlField.getText().trim();
							Object selectedModel = modelCombo.getSelectedItem();
							launchSettings.openaiModel = selectedModel != null ? selectedModel.toString().trim() : "";
							launchSettings.vision = visionCheckBox.isSelected();
							launchSettings.reasoningLevel = (String) reasoningCombo.getSelectedItem();
							launchSettings.maxActions = (Integer) actionsSpinner.getValue();
							launchSettings.numRuns = totalRuns;
							launchSettings.bddScenarioName = bddScenarioNameField.getText().trim();
							launchSettings.bddInstructions = normalizeBddInstructions(bddInstructionsTextArea.getText());

							LlmMcpAgent llmMcpAgent = new LlmMcpAgent(
									sMainFrame.getProject(),
									launchSettings
							);
							lastResult = llmMcpAgent.runLLMAgent();
						}

						return lastResult;
					}

					@Override
					protected void done() {
						launchButton.setText("Launch");
						setDialogBusy(dialog, false);
						try {
							String result = get();
							if (result != null && !result.trim().isEmpty()) {
								Notification.show(result);
							}
						} catch (Exception ex) {
							java.util.logging.Logger.getLogger(MCPAgentPanel.class.getName()).log(
									java.util.logging.Level.SEVERE,
									"TESTAR MCP agent execution failed",
									ex
							);
							String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
							Notification.show(message != null ? message : "TESTAR MCP agent execution failed.");
						}
					}
				};
				worker.execute();
			}
		});

		closeButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				saveFromUi.run();
				reloadReusableTree();
				dialog.dispose();
				sMainFrame.checkAndLoadRecent();
			}
		});

		dialog.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				saveFromUi.run();
				reloadReusableTree();
				super.windowClosing(e);
			}
		});

		buttonPanel.add(launchButton);
		buttonPanel.add(closeButton);

		dialog.add(inputPanel, BorderLayout.CENTER);
		dialog.add(buttonPanel, BorderLayout.SOUTH);

		dialog.setLocationRelativeTo(sMainFrame);
		dialog.setVisible(true);
	}

	private McpAgentSettings loadSettings() {
		try {
			if (Files.exists(SETTINGS_PATH)) {
				return JSON_MAPPER.readValue(SETTINGS_PATH.toFile(), McpAgentSettings.class);
			}
		} catch (IOException e) {
			java.util.logging.Logger.getLogger(MCPAgentPanel.class.getName()).log(
					java.util.logging.Level.SEVERE,
					e.getMessage()
			);
		}
		return new McpAgentSettings();
	}

	private void saveSettings(McpAgentSettings settings) {
		try {
			if (SETTINGS_PATH.getParent() != null) {
				Files.createDirectories(SETTINGS_PATH.getParent());
			}
			JSON_MAPPER.writerWithDefaultPrettyPrinter()
					.writeValue(SETTINGS_PATH.toFile(), settings);
		} catch (IOException e) {
			java.util.logging.Logger.getLogger(MCPAgentPanel.class.getName()).log(
					java.util.logging.Level.SEVERE,
					e.getMessage()
			);
		}
	}

	private void setComponentsEnabled(Container container, boolean enabled) {
		for (Component c : container.getComponents()) {
			c.setEnabled(enabled);
			if (c instanceof Container) {
				setComponentsEnabled((Container) c, enabled);
			}
		}
	}

	private void setDialogBusy(JDialog dialog, boolean busy) {
		setComponentsEnabled(dialog.getContentPane(), !busy);
		dialog.setCursor(busy
				? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)
				: Cursor.getDefaultCursor());
	}

	private String normalizeBddInstructions(String text) {
		if (text == null) {
			return "";
		}

		return text
				.replace('\u2018', '\'')
				.replace('\u2019', '\'')
				.replace('\u201C', '\'')
				.replace('\u201D', '\'');
	}

	private void reloadReusableTree() {
		try {
			if (sMainFrame.getProject() == null || sMainFrame.getTestDesign() == null) {
				return;
			}
			ReusableTreeModel model = sMainFrame.getTestDesign().getReusableTree().getTreeModel();
			model.setProject(sMainFrame.getProject());
			model.reload();
		} catch (Exception ex) {
			java.util.logging.Logger.getLogger(MCPAgentPanel.class.getName()).log(
					java.util.logging.Level.SEVERE,
					"Failed to reload the reusable tree from the project state"
			);
			java.util.logging.Logger.getLogger(MCPAgentPanel.class.getName()).log(
					java.util.logging.Level.SEVERE,
					ex.getMessage()
			);
		}
	}

	private String[] getProviderModels(String providerName) {
		String normalizedProvider = LlmProviderFactory.normalizeProviderName(providerName);
		if (LlmProviderFactory.PROVIDER_GEMINI.equals(normalizedProvider)) {
			return new String[] { "gemini-2.5-flash", "gemini-3.5-flash" };
		}
		if (LlmProviderFactory.PROVIDER_OLLAMA.equals(normalizedProvider)) {
			return new String[] { "qwen3.5:2b", "qwen3.5:4b", "qwen3.5:9b", "llama3.1:8b", "llama3.2:3b", "ministral-3:3b", "ministral-3:8b" };
		}
		return new String[] { "gpt-5-mini", "gpt-5.4-mini", "gpt-5.4-nano" };
	}

}
