package com.ing.ide.main.testar;

import com.ing.datalib.component.Project;
import com.ing.datalib.component.Reusable;
import com.ing.datalib.component.Scenario;
import com.ing.datalib.component.TestCase;
import com.ing.datalib.component.TestStep;
import com.ing.datalib.or.ObjectRepository;
import com.ing.datalib.or.common.ObjectGroup;
import com.ing.datalib.or.web.WebOR;
import com.ing.datalib.or.web.WebORObject;
import com.ing.datalib.or.web.WebORPage;
import com.ing.datalib.testdata.model.TestDataModel;
import com.ing.ide.main.mainui.components.testdesign.tree.model.ReusableTreeModel;
import com.ing.ide.main.testar.playwright.actions.PlaywrightClick;
import com.ing.ide.main.testar.playwright.actions.PlaywrightFill;
import com.ing.ide.main.testar.playwright.actions.PlaywrightSelect;
import com.ing.ide.main.testar.playwright.system.PlaywrightState;
import com.ing.ide.main.testar.playwright.system.PlaywrightTags;
import com.ing.ide.main.testar.playwright.system.PlaywrightWidget;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.testar.monkey.alayer.Action;
import org.testar.monkey.alayer.Tags;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public class TESTARDataWriter {

    private final Project project;
    private final ObjectRepository objectRepository;
    private final WebOR webOR;
    private final Scenario bddScenario;
    private final TestCase mainTestCase;
    private final String bddScenarioName;

    private final Scenario reusableStepScenario;
    private final String REUSABLE_GROUP_NAME = "AI-BDD mapping";

    public TESTARDataWriter(Project project, String bddScenarioName) {
        // Prepare the INGenious Object Repository used to store steps information
        // This OR is common to all scenarios and test cases
        this.project = project;
        this.bddScenarioName = bddScenarioName;
        objectRepository = new ObjectRepository(project);
        webOR = objectRepository.getWebOR();
        webOR.setObjectRepository(objectRepository);

        // Prepare a high-level BDD Scenario in the Test Plan
        this.bddScenario = setBddScenario();

        // Prepare a TestCase in the created high-level BDD Scenario
        this.mainTestCase = setMainTestCase();

        // Prepare a resuable Scenario for low-level step definitions
        this.reusableStepScenario = setResuableStepScenario();
    }

    private Scenario setBddScenario() {
        // Create a scenario version of the high-level BDD-MCP scenario
        String bddScenarioName = "BDD-MCP_scenario";
        // Attach the scenario to the Project instance, to be visible to reusable persistence.
        Scenario createdBddScenario = this.project.addScenario(bddScenarioName);
        return createdBddScenario != null ? createdBddScenario : this.project.getScenarioByName(bddScenarioName);
    }

    private TestCase setMainTestCase() {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String scenarioPrefix = sanitizeScenarioName(bddScenarioName);
        String testCaseName = scenarioPrefix.isEmpty()
                ? timestamp
                : scenarioPrefix + "_" + timestamp;
        return this.bddScenario.addTestCase(testCaseName);
    }

    private String sanitizeScenarioName(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String sanitized = trimmed.replaceAll("[\\\\/?:*\"|><\r\n]", "_");
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50).trim() + "_" + Math.abs(value.hashCode());
        }
        return sanitized;
    }

    private Scenario setResuableStepScenario() {
        // Prepare an INGenious low-level steps scenario to be a reusable component
        String resuableStepDefinitionsScenarioName = "StepDefinitions_" + this.mainTestCase.getName();
        Scenario existingReusableStepScenario = this.project.getScenarioByName(resuableStepDefinitionsScenarioName);
        if (existingReusableStepScenario != null) {
            return existingReusableStepScenario;
        }
        Scenario created = this.project.addScenario(resuableStepDefinitionsScenarioName);
        return created != null ? created : this.project.getScenarioByName(resuableStepDefinitionsScenarioName);
    }

    /** Add the action-element info into INGenious */
    public WebORObject addWidgetObject(PlaywrightWidget playwrightWidget, Page page) {
        WebORPage webORPage = webOR.addPage(page.title());

        String elementDescription = describeElement(playwrightWidget);
        ObjectGroup objectGroup = webORPage.addObjectGroup(elementDescription);
        WebORObject webORObject = (WebORObject) objectGroup.addObject(elementDescription);

        String text = playwrightWidget.get(PlaywrightTags.WebLocatorText);
        if (text != null && !text.isEmpty()) {
            String evaluatedText = evaluateLocatorWithExactFallback(
                    exact -> {
                        Page.GetByTextOptions opts = new Page.GetByTextOptions().setExact(exact);
                        return page.getByText(text, opts);
                    }, text
            );
            webORObject.setAttributeByName("Text", evaluatedText);
        }

        String label = playwrightWidget.get(PlaywrightTags.WebLocatorLabel);
        if (label != null && !label.isEmpty()) {
            String evaluatedLabel = evaluateLocatorWithExactFallback(
                    exact -> {
                        Page.GetByLabelOptions opts = new Page.GetByLabelOptions().setExact(exact);
                        return page.getByLabel(label, opts);
                    }, label
            );
            webORObject.setAttributeByName("Label", evaluatedLabel);
        }

        webORObject.setAttributeByName("Role", playwrightWidget.get(PlaywrightTags.WebLocatorRole));
        webORObject.setXpath(playwrightWidget.get(PlaywrightTags.WebLocatorXPath));
        webORObject.setCss(playwrightWidget.get(PlaywrightTags.WebLocatorCSS));
        webORObject.setAttributeByName("Placeholder", playwrightWidget.get(PlaywrightTags.WebLocatorPlaceholder));
        webORObject.setAttributeByName("AltText", playwrightWidget.get(PlaywrightTags.WebLocatorAltText));
        webORObject.setAttributeByName("Title", playwrightWidget.get(PlaywrightTags.WebLocatorTitle));
        webORObject.setAttributeByName("TestId", playwrightWidget.get(PlaywrightTags.WebLocatorTestId));

        return webORObject;
    }

    private String evaluateLocatorWithExactFallback(Function<Boolean, Locator> locatorBuilder, String value) {
        try {
            Locator locator = locatorBuilder.apply(false);
            int count = locator.count();
            if (count == 1) return value;

            // If default text or label locator matches more than 1 element
            // Try to automatically consider an exact locator
            if (count > 1) {
                Locator exactLocator = locatorBuilder.apply(true);
                int exactCount = exactLocator.count();
                if (exactCount == 1) return value + ";exact";
            }
        } catch (Exception e) {
            java.util.logging.Logger.getLogger(TESTARDataWriter.class.getName()).log(
                    java.util.logging.Level.SEVERE,
                    "Locator evaluation failed for '{" +value  + "}'"
            );
            java.util.logging.Logger.getLogger(TESTARDataWriter.class.getName()).log(
                    java.util.logging.Level.SEVERE,
                    e.getMessage()
            );
        }
        return value;
    }

    private String describeElement(PlaywrightWidget widget) {
        // Get visual element descriptor
        String name = getFirstNonEmpty(
                widget.get(PlaywrightTags.WebInnerText, "").trim(),
                widget.get(PlaywrightTags.WebLocatorLabel, "").trim(),
                widget.get(PlaywrightTags.WebName, ""),
                widget.get(PlaywrightTags.WebId, ""),
                widget.get(PlaywrightTags.WebAriaLabel, ""),
                widget.get(PlaywrightTags.WebPlaceholder, ""),
                widget.get(PlaywrightTags.WebValue, "")
        );
        String descriptor = name.replaceAll("\\s+", "");
        if (descriptor.isEmpty()) descriptor = "unnamed";

        // Get type of the element
        String tag = widget.get(PlaywrightTags.WebTagName, "");
        String type = widget.get(PlaywrightTags.WebType, "");
        String label;
        switch (tag) {
            case "button":
                label = "[button]";
                break;
            case "a":
                label = "[link]";
                break;
            case "input":
                if ("submit".equalsIgnoreCase(type) || "button".equalsIgnoreCase(type)) {
                    label = "[input-button]";
                } else {
                    label = "[input]";
                }
                break;
            case "select":
                // Handle <select> elements
                ElementHandle selectedOption = widget.getElementHandle().querySelector("option[selected]");
                if (selectedOption == null) {
                    selectedOption = widget.getElementHandle().querySelector("option");
                }
                String selectedText = selectedOption != null ? selectedOption.innerText().trim() : "";
                String selectDescriptor = getFirstNonEmpty(
                        widget.get(PlaywrightTags.WebName, ""),
                        widget.get(PlaywrightTags.WebId, ""),
                        selectedText
                );
                descriptor = selectDescriptor.replaceAll("\\s+", "");
                if (descriptor.isEmpty()) descriptor = "unnamed";
                label = "[select]";
                break;
            case "textarea":
                label = "[textarea]";
                break;
            case "label":
                label = "[label]";
                break;
            case "img":
                label = "[image]";
                break;
            case "svg":
                label = "[icon]";
                break;
            default:
                label = "[" + tag + "]";
                break;
        }

        return descriptor + label;
    }

    private String getFirstNonEmpty(String... values) {
        for (String val : values) {
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        }
        return "";
    }

    public void addAbstractTestStep(
            String bddStep,
            String object,
            String description,
            String testAction,
            String input,
            String reference
    ) {
        String bddStepParsed = bddStep.replaceAll("[\\\\/?:*\"|><\r\n]", "_");
        if (bddStepParsed.length() > 50) {
            bddStepParsed = bddStepParsed.substring(0, 50).trim() + "_" + Math.abs(bddStep.hashCode());
        }
        String bddAction = this.reusableStepScenario.getName() + ":".concat(bddStepParsed);

        // First, create the abstract test step in the main test case of the test plan
        // Only if it does not already exist
        boolean exists = mainTestCase.getTestSteps()
                .stream()
                .anyMatch(testStep -> bddAction.equals(testStep.getAction()));
        if(!exists) {
            TestStep abstractTestStep = mainTestCase.addNewStep();
            abstractTestStep.setObject("Execute");
            abstractTestStep.setDescription(bddStep);
            abstractTestStep.setAction(bddAction);
        }

        // Second, create a test case in the reusable scenario
        // Only if it does not already exist
        TestCase reusableTestCase = reusableStepScenario.getTestCaseByName(bddStepParsed);
        if(reusableTestCase == null) {
            reusableTestCase = reusableStepScenario.addTestCase(bddStepParsed);
        }
        setReusableTestCase(reusableTestCase);
        TestStep concreteTestStep = reusableTestCase.addNewStep();
        concreteTestStep.setObject(object);
        concreteTestStep.setDescription(description);
        concreteTestStep.setAction(testAction);
        concreteTestStep.setInput(createTestDataInput(input, object, testAction));
        concreteTestStep.setReference(reference);
        reusableTestCase.save();
    }

    public void addConcreteTestStep(
            String object,
            String description,
            String testAction,
            String input,
            String reference
    ) {
        TestStep concreteTestStep = mainTestCase.addNewStep();
        concreteTestStep.setObject(object);
        concreteTestStep.setDescription(description);
        concreteTestStep.setAction(testAction);
        concreteTestStep.setInput(createTestDataInput(input, object, testAction));
        concreteTestStep.setReference(reference);
    }

    /** Add the TestStep into INGenious */
    public void addActionTestStep(String bddStep, PlaywrightState state, Action action, Page page) {
        // Add the state-page to the OR
        String webPageTitle = state.getPage().title();
        WebORPage webORPage = webOR.addPage(webPageTitle);
        // Add the data of the selected action-element into the OR
        WebORObject webORObject = addWidgetObject((PlaywrightWidget) action.get(Tags.OriginWidget), page);

        // Create an INGenious action test step
        String object = webORObject.getName();
        String description = "";
        String testAction = "";
        String input = "";
        String reference = webORPage.getName();

        if(action instanceof PlaywrightClick) {
            description = "Click the [<Object>]";
            testAction = "Click";
        }
        else if(action instanceof PlaywrightFill) {
            description = "Enter the value [<Data>] in the Field [<Object>]";
            testAction = "Fill";
            input = "@" + ((PlaywrightFill)action).getTypedText();
        }
        else if(action instanceof PlaywrightSelect) {
            description = "Select item in [<Object>] which has text: [<Data>]";
            testAction = "SelectSingleByText";
            input = "@" + ((PlaywrightSelect)action).getOptionValue();
        }
        else testAction = "Unknown";

        addAbstractTestStep(
                bddStep,
                object,
                description,
                testAction,
                input,
                reference
        );
    }

    public void addAssertTestStep(String bddStep, PlaywrightState state, String assertText) {
        // Add the state-page to the OR
        String webPageTitle = state.getPage().title();
        WebORPage webORPage = webOR.addPage(webPageTitle);

        // Add the element to the OR
        String trimmedText = assertText.length() > 10 ? assertText.substring(0, 10) : assertText;
        String elementDescription = "assert" + trimmedText + "[text]";
        ObjectGroup objectGroup = webORPage.addObjectGroup(elementDescription);
        WebORObject webORObject = (WebORObject) objectGroup.addObject(elementDescription);
        webORObject.setAttributeByName("Text", assertText);

        // Create an INGenious action test step
        String object = webORObject.getName();
        String description = "Assert if [<Object>] is visible";
        String testAction = "assertElementIsVisible";
        String input = "";
        String reference = webORPage.getName();

        addAbstractTestStep(
                bddStep,
                object,
                description,
                testAction,
                input,
                reference
        );
    }

    public void saveExecutionSteps() {
        this.mainTestCase.save();
        this.bddScenario.save();
        this.reusableStepScenario.save();
        this.objectRepository.save();
        this.project.save();

        ReusableTreeModel reusableTreeModel = new ReusableTreeModel();
        reusableTreeModel.setProject(this.project);
        reusableTreeModel.save();
    }

    private void setReusableTestCase(TestCase reusableTestCase) {
        if (reusableTestCase == null) {
            return;
        }
        Reusable reusable = reusableTestCase.getReusable();
        if (reusable == null) {
            reusable = new Reusable();
            reusableTestCase.setReusable(reusable);
        }
        if (reusable.getGroup() == null || reusable.getGroup().trim().isEmpty()) {
            reusable.setGroup(REUSABLE_GROUP_NAME);
        }
    }

    private String createTestDataInput(String input, String object, String testAction) {
        if (input == null 
            || input.trim().isEmpty() 
            || !input.trim().startsWith("@")
            || input.trim().substring(1).isEmpty()) {
            return input;
        }

        // Extract the value from the provided data input @john -> john
        String inputValue = input.trim().substring(1);

        // The sheet data name is the same as the one created in the BDD-MCP scenario
        String sheetDataName = mainTestCase.getName();
        // Identify the column data by combining the ObjectName and Action
        // TODO: Determine is this is a reliable unique column name or refactor it
        String columnDataName = object + "_" + testAction; // e.g., (Browser_Open, username[input]_Fill, etc.)

        try {
            TestDataModel testDataModel = project.getTestData().defData().getByName(sheetDataName);
            if (testDataModel == null) {
                testDataModel = project.getTestData().defData().addTestData();
                testDataModel.rename(sheetDataName);
                // remove these default data columns
                testDataModel.removeColumn("Data1");
                testDataModel.removeColumn("Data2");
            }
            testDataModel.loadTableModel();

            int rowIndex = findOrCreateDataRow(testDataModel);
            String columnDataField = findOrCreateColumnDataField(testDataModel, columnDataName, inputValue, rowIndex);

            int colIndex = testDataModel.getColumnIndex(columnDataField);
            if (colIndex >= 0) {
                testDataModel.setValueAt(inputValue, rowIndex, colIndex);
            }

            return sheetDataName + ":" + columnDataField;
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(TESTARDataWriter.class.getName()).log(
                    java.util.logging.Level.WARNING,
                    "Failed to associate input with test data; leaving literal input as-is: " + ex.getMessage()
            );
            return input;
        }
    }

    private int findOrCreateDataRow(TestDataModel testDataModel) {
        String scenarioName = bddScenario.getName();
        String testCaseName = mainTestCase.getName();
        String iteration = "1";
        String subIteration = "1";

        // If the first four identification columns exist, return the existing row
        List<?> records = testDataModel.getRecords();
        for (int i = 0; i < records.size(); i++) {
            Object recordObj = records.get(i);
            if (recordObj instanceof List) {
                List<?> record = (List<?>) recordObj;
                if (record.size() >= 4
                        && scenarioName.equals(String.valueOf(record.get(0)))
                        && testCaseName.equals(String.valueOf(record.get(1)))
                        && iteration.equals(String.valueOf(record.get(2)))
                        && subIteration.equals(String.valueOf(record.get(3)))) {
                    return i;
                }
            }
        }

        // If the row does not exist, create it with the four identified columns
        testDataModel.addRecord();
        int rowIndex = Math.max(0, testDataModel.getRowCount() - 1);
        testDataModel.setValueAt(scenarioName, rowIndex, testDataModel.getColumnIndex("Scenario"));
        testDataModel.setValueAt(testCaseName, rowIndex, testDataModel.getColumnIndex("Flow"));
        testDataModel.setValueAt(iteration, rowIndex, testDataModel.getColumnIndex("Iteration"));
        testDataModel.setValueAt(subIteration, rowIndex, testDataModel.getColumnIndex("SubIteration"));
        return rowIndex;
    }

    private String findOrCreateColumnDataField(TestDataModel testDataModel, String columnDataName, String inputValue, int rowIndex) {
        String columnDataField = columnDataName;
        int suffix = 2;

        while (true) {
            // If the column data field does not exist in the row, create it
            // E.g., add a new non existing column data field (username[input]_Fill)
            if (!testDataModel.hasColumn(columnDataField)) {
                testDataModel.addColumn(columnDataField);
                return columnDataField;
            }
            // If the column data field exists in the row, check the input value
            int colIndex = testDataModel.getColumnIndex(columnDataField);
            String existing = Objects.toString(testDataModel.getValueAt(rowIndex, colIndex), "").trim();
            // If the input value is empty or is the same, just return the column data field
            // E.g., return an existing column data field (username[input]_Fill == john)
            if (existing.isEmpty() || existing.equals(inputValue)) {
                return columnDataField;
            }
            // Else, an existing column data field exists, so we need to create a unique one
            // E.g., try to create a new unique column data field for (username[input]_Fill_2)
            columnDataField = columnDataName + "_" + suffix++;
        }
    }

}
