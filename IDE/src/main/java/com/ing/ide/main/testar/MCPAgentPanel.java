package com.ing.ide.main.testar;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ing.ide.main.mainui.AppMainFrame;
import com.ing.ide.main.testar.mcp.LlmMcpAgent;
import com.ing.ide.main.testar.mcp.McpAgentSettings;
import com.ing.ide.settings.IconSettings;

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

	private McpAgentSettings settings;
	private static final Path SETTINGS_PATH = Paths.get(System.getProperty("user.home"),
			".ingenious-mcp-settings.json");
	private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

	private String defaultBDDText = "Given the user navigates to the url 'https://para.testar.org/'\n" +
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

		JPanel formPanel = new JPanel(new GridLayout(9, 2, 5, 5));

		JLabel providerLabel = new JLabel("LLM Provider:");
		String[] providers = { "OpenAI", "Gemini", "Ollama" };
		JComboBox<String> providerCombo = new JComboBox<>(providers);
		String providerDefault = settings.llmProviderName != null ? settings.llmProviderName : "OpenAI";
		providerCombo.setSelectedItem(providerDefault);
		formPanel.add(providerLabel);
		formPanel.add(providerCombo);

		JLabel customUrlLabel = new JLabel("Custom API URL:");
		JTextField customUrlField = new JTextField(100);
		String customUrlDefault = settings.customApiUrl != null ? settings.customApiUrl : "";
		customUrlField.setText(customUrlDefault);
		formPanel.add(customUrlLabel);
		formPanel.add(customUrlField);

		JLabel apiUrlLabel = new JLabel("API URL:");
		JTextField apiUrlField = new JTextField(100);
		String apiUrlDefault = settings.apiUrl != null
				? settings.apiUrl
				: "https://api.openai.com/v1/chat/completions";
		apiUrlField.setText(apiUrlDefault);
		formPanel.add(apiUrlLabel);
		formPanel.add(apiUrlField);

		JLabel apiKeyEnvVarLabel = new JLabel("API key env var:");
		JTextField apiKeyEnvVarField = new JTextField(40);
		String apiEnvDefault = settings.apiKeyEnvVarName != null
				? settings.apiKeyEnvVarName
				: "OPENAI_API_KEY";
		apiKeyEnvVarField.setText(apiEnvDefault);
		formPanel.add(apiKeyEnvVarLabel);
		formPanel.add(apiKeyEnvVarField);

		JLabel openaiLabel = new JLabel("Model:");
		// Editable combobox — options change based on the selected provider
		String[] ollamaModels = { "ministral-3:8b", "qwen2.5:7b", "qwen3:8b", "llama3.1", "llama3.2" };
		String[] openaiModels = { "gpt-5.4-mini", "gpt-5.4-nano", "gpt-4.1", "gpt-4o", "gpt-4.1-mini", "gpt-4o-mini", "o4-mini", "o3-mini" };
		String[] geminiModels = { "gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash" };

		String[] initialModels;
		if ("Ollama".equals(providerDefault))       initialModels = ollamaModels;
		else if ("Gemini".equals(providerDefault))  initialModels = geminiModels;
		else                                        initialModels = openaiModels;

		JComboBox<String> modelCombo = new JComboBox<>(initialModels);
		modelCombo.setEditable(true); // allows typing any custom model tag
		String modelDefault;
		if (settings.openaiModel != null && !settings.openaiModel.isBlank()) {
			modelDefault = settings.openaiModel;
		} else {
			modelDefault = initialModels[0];
		}
		modelCombo.setSelectedItem(modelDefault);
		formPanel.add(openaiLabel);
		formPanel.add(modelCombo);

		JLabel visionLabel = new JLabel("Vision (if applies):");
		JCheckBox visionCheckBox = new JCheckBox("Enable vision");
		boolean visionDefault = settings.vision != null ? settings.vision : false;
		visionCheckBox.setSelected(visionDefault);
		formPanel.add(visionLabel);
		formPanel.add(visionCheckBox);

		JLabel reasoningLabel = new JLabel("Reasoning effort:");
		String[] reasoningOptions = { "none", "minimal", "low", "medium", "high" };
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

		JLabel numRunsLabel = new JLabel("Num Runs (batch):");
		JSpinner numRunsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 50, 1));
		int numRunsDefault = settings.numRuns != null ? settings.numRuns : 1;
		numRunsSpinner.setValue(numRunsDefault);
		formPanel.add(numRunsLabel);
		formPanel.add(numRunsSpinner);

		providerCombo.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				String selected = (String) providerCombo.getSelectedItem();
				modelCombo.removeAllItems();
				if ("Gemini".equals(selected)) {
					for (String m : geminiModels) modelCombo.addItem(m);
					customUrlField.setText("");
					apiUrlField.setText("");
					apiKeyEnvVarField.setText("GEMINI_API_KEY");
					modelCombo.setSelectedItem("gemini-2.5-flash");
					visionCheckBox.setSelected(true);
					reasoningCombo.setSelectedItem("none");
				} else if ("OpenAI".equals(selected)) {
					for (String m : openaiModels) modelCombo.addItem(m);
					customUrlField.setText("");
					apiUrlField.setText("https://api.openai.com/v1/chat/completions");
					apiKeyEnvVarField.setText("OPENAI_API_KEY");
					modelCombo.setSelectedItem("gpt-4o");
					visionCheckBox.setSelected(true);
					reasoningCombo.setSelectedItem("none");
				} else if ("Ollama".equals(selected)) {
					for (String m : ollamaModels) modelCombo.addItem(m);
					customUrlField.setText("http://localhost:11434/api/chat");
					apiUrlField.setText("");
					apiKeyEnvVarField.setText("");
					modelCombo.setSelectedItem("ministral-3:8b");
					visionCheckBox.setSelected(false);
					reasoningCombo.setSelectedItem("none");
				}
			}
		});

		inputPanel.add(formPanel, BorderLayout.NORTH);

		// Add a BDD Instructions text area with scroll
		JLabel bddLabel = new JLabel("BDD Instructions:");
		String bddDefault = settings.bddText != null ? settings.bddText : defaultBDDText;
		JTextArea bddTextArea = new JTextArea(bddDefault, 10, 40);
		bddTextArea.setLineWrap(true);
		bddTextArea.setWrapStyleWord(true);
		JScrollPane bddScrollPane = new JScrollPane(bddTextArea);

		JPanel bddPanel = new JPanel(new BorderLayout());
		bddPanel.add(bddLabel, BorderLayout.NORTH);
		bddPanel.add(bddScrollPane, BorderLayout.CENTER);

		inputPanel.add(bddPanel, BorderLayout.CENTER);

		// Create a panel for buttons
		JPanel buttonPanel = new JPanel();
		JButton launchButton = new JButton("Launch");
		JButton closeButton = new JButton("Close");

		Runnable saveFromUi = () -> {
			settings.llmProviderName = (String) providerCombo.getSelectedItem();
			settings.customApiUrl = customUrlField.getText().trim();
			settings.apiUrl = apiUrlField.getText().trim();
			settings.apiKeyEnvVarName = apiKeyEnvVarField.getText().trim();
			Object selectedModel = modelCombo.getSelectedItem();
			settings.openaiModel = selectedModel != null ? selectedModel.toString().trim() : "";
			settings.vision = visionCheckBox.isSelected();
			settings.reasoningLevel = (String) reasoningCombo.getSelectedItem();
			settings.maxActions = (Integer) actionsSpinner.getValue();
			settings.numRuns = (Integer) numRunsSpinner.getValue();
			settings.bddText = bddTextArea.getText();

			// keep in-memory default in sync as well
			defaultBDDText = settings.bddText;

			saveSettings(settings);
		};

		launchButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				saveFromUi.run();

				final int totalRuns = settings.numRuns != null ? settings.numRuns : 1;
				final int maxActions = settings.maxActions != null ? settings.maxActions : 10;

				// Disable interaction with the dialog panel elements
				setComponentsEnabled(dialog.getContentPane(), false);
				dialog.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

				SwingWorker<Void, String> worker = new SwingWorker<Void, String>() {
					@Override
					protected Void doInBackground() throws Exception {
						for (int i = 1; i <= totalRuns; i++) {
							// Update button label with progress on the EDT
							final int current = i;
							SwingUtilities.invokeLater(() ->
								launchButton.setText("Run " + current + "/" + totalRuns + " …")
							);

							// Each run gets its own fresh LlmProvider and LlmMcpAgent
							com.ing.ide.main.testar.mcp.LlmProvider llmProvider =
								com.ing.ide.main.testar.mcp.LlmProviderFactory.getProvider(settings);
							LlmMcpAgent llmMcpAgent = new LlmMcpAgent(
								sMainFrame.getProject(),
								llmProvider,
								maxActions,
								settings.bddText);
							llmMcpAgent.runLLMAgent();
						}
						return null;
					}

					@Override
					protected void done() {
						launchButton.setText("Launch");
						setComponentsEnabled(dialog.getContentPane(), true);
						dialog.setCursor(Cursor.getDefaultCursor());
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
					e.getMessage());
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
					e.getMessage());
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

	private void reloadReusableTree() {
		try {
			if (sMainFrame.getProject() == null || sMainFrame.getTestDesign() == null) {
				return;
			}
			com.ing.ide.main.mainui.components.testdesign.tree.model.ReusableTreeModel model =
					sMainFrame.getTestDesign().getReusableTree().getTreeModel();
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

}
