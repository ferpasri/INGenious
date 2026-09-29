package com.ing.ide.main.testar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ing.datalib.component.EnvTestData;
import com.ing.datalib.component.Project;
import com.ing.datalib.component.Scenario;
import com.ing.datalib.component.TestCase;
import com.ing.datalib.component.TestData;
import com.ing.datalib.component.TestStep;
import com.ing.datalib.or.ObjectRepository;
import com.ing.datalib.or.common.ObjectGroup;
import com.ing.datalib.or.web.WebORObject;
import com.ing.datalib.or.web.WebORPage;
import com.ing.datalib.testdata.model.Record;
import com.ing.datalib.testdata.model.TestDataModel;
import com.ing.ide.main.testar.playwright.system.PlaywrightState;
import com.ing.ide.main.testar.playwright.system.PlaywrightTags;
import com.ing.ide.main.testar.playwright.system.PlaywrightWidget;
import com.ing.ide.main.testar.service.persistence.INGeniousPersistenceService;
import com.ing.ide.main.testar.trace.AgentTraceContext;
import com.ing.ide.main.testar.trace.AgentTraceSupport;
import com.ing.ide.main.testar.trace.ArtifactPersistenceResult;
import com.ing.ide.main.testar.trace.ArtifactReference;
import com.ing.ide.main.testar.trace.JsonlAgentTraceRecorder;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

public class TESTARDataWriterTest {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private Path tempDir;

    @AfterMethod
    public void tearDown() throws IOException {
        if (tempDir == null || !Files.exists(tempDir)) {
            return;
        }

        Files
            .walk(tempDir)
            .sorted(Comparator.reverseOrder())
            .forEach(
                path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException exception) {
                        throw new RuntimeException(exception);
                    }
                }
            );
    }

    @Test
    public void testAddNewPageWithWidgets() throws IOException {
        TESTARDataWriter writer = createWriter();
        Page page = mockPage("Request Loan");
        PlaywrightWidget widget = mockWidget(
            "Apply Now",
            "button",
            "",
            "",
            "button",
            "button",
            "//button[@id='apply']",
            "#apply",
            "",
            "",
            "Apply title",
            "apply-button"
        );

        WebORObject object = writer.addWidgetObject(widget, page);

        assertThat(object.getName()).isEqualTo("ApplyNow[button]");
        assertThat(object.getAttributeByName("Role")).isEqualTo("button");
        assertThat(object.getAttributeByName("xpath")).isEqualTo("//button[@id='apply']");
        assertThat(object.getAttributeByName("css")).isEqualTo("#apply");
        assertThat(object.getAttributeByName("Title")).isEqualTo("Apply title");
        assertThat(object.getAttributeByName("TestId")).isEqualTo("apply-button");

        WebORPage webORPage = (WebORPage) object.getParent().getParent();
        assertThat(webORPage.getName()).isEqualTo("Request Loan");
        assertThat(webORPage.getObjectGroups()).hasSize(1);
    }

    @Test
    public void testAddNewWidgetToExistingPage() throws IOException {
        TESTARDataWriter writer = createWriter();
        Page page = mockPage("Request Loan");

        WebORObject firstObject = writer.addWidgetObject(
            mockWidget(
                "Apply Now",
                "button",
                "",
                "",
                "button",
                "button",
                "//button[@id='apply']",
                "#apply",
                "",
                "",
                "",
                ""
            ),
            page
        );

        WebORObject secondObject = writer.addWidgetObject(
            mockWidget(
                "Cancel",
                "button",
                "",
                "",
                "button",
                "button",
                "//button[@id='cancel']",
                "#cancel",
                "",
                "",
                "",
                ""
            ),
            page
        );

        WebORPage firstPage = (WebORPage) firstObject.getParent().getParent();
        WebORPage secondPage = (WebORPage) secondObject.getParent().getParent();

        assertThat(secondPage).isSameAs(firstPage);
        assertThat(firstPage.getObjectGroups()).hasSize(2);
        assertThat(secondObject.getName()).isEqualTo("Cancel[button]");
        assertThat(secondObject.getAttributeByName("css")).isEqualTo("#cancel");
    }

    @Test
    public void testReuseWidgetOfExistingPage() throws IOException {
        TESTARDataWriter writer = createWriter();
        Page page = mockPage("Request Loan");
        PlaywrightWidget widget = mockWidget(
            "Apply Now",
            "button",
            "",
            "",
            "button",
            "button",
            "//button[@id='apply']",
            "#apply",
            "",
            "",
            "",
            ""
        );

        WebORObject firstObject = writer.addWidgetObject(widget, page);
        WebORObject secondObject = writer.addWidgetObject(widget, page);

        WebORPage webORPage = (WebORPage) firstObject.getParent().getParent();
        ObjectGroup<?> objectGroup = firstObject.getParent();

        assertThat(secondObject).isSameAs(firstObject);
        assertThat(webORPage.getObjectGroups()).hasSize(1);
        assertThat(objectGroup.getObjects()).hasSize(1);
    }

    @Test
    public void testSameWidgetNameWithDifferentLocatorsCreatesDifferentObjects()
        throws IOException {
        TESTARDataWriter writer = createWriter();
        Page page = mockPage("Request Loan");

        WebORObject firstObject = writer.addWidgetObject(
            mockWidget(
                "Continue",
                "button",
                "",
                "",
                "button",
                "button",
                "//button[@id='continue-a']",
                "#continue-a",
                "",
                "",
                "",
                ""
            ),
            page
        );
        WebORObject secondObject = writer.addWidgetObject(
            mockWidget(
                "Continue",
                "button",
                "",
                "",
                "button",
                "button",
                "//button[@id='continue-b']",
                "#continue-b",
                "",
                "",
                "",
                ""
            ),
            page
        );

        WebORPage webORPage = (WebORPage) firstObject.getParent().getParent();

        assertThat(secondObject).isNotSameAs(firstObject);
        assertThat(webORPage.getObjectGroups()).hasSize(2);
        assertThat(firstObject.getAttributeByName("css")).isEqualTo("#continue-a");
        assertThat(secondObject.getAttributeByName("css")).isEqualTo("#continue-b");
        assertThat(secondObject.getName()).isEqualTo("Continue_2[button]");

        Path pageFile = tempDir
            .resolve("ObjectRepository")
            .resolve("Web")
            .resolve("Request Loan.yaml");
        assertThat(Files.readString(pageFile))
            .contains("Continue[button]:", "Continue_2[button]:", "#continue-a", "#continue-b");
    }

    @Test
    public void testSameWidgetWithChangedDescriptionReusesMatchingLocator() throws IOException {
        TESTARDataWriter writer = createWriter();
        Page page = mockPage("Request Loan");

        WebORObject firstObject = writer.addWidgetObject(
            mockWidget(
                "Continue",
                "button",
                "",
                "",
                "button",
                "button",
                "//button[@id='continue']",
                "#continue",
                "",
                "",
                "",
                ""
            ),
            page
        );
        WebORObject secondObject = writer.addWidgetObject(
            mockWidget(
                "Continue to review",
                "button",
                "",
                "",
                "button",
                "button",
                "//button[@id='continue']",
                "#continue",
                "",
                "",
                "",
                ""
            ),
            page
        );

        WebORPage webORPage = (WebORPage) firstObject.getParent().getParent();

        assertThat(secondObject).isSameAs(firstObject);
        assertThat(webORPage.getObjectGroups()).hasSize(1);
        assertThat(secondObject.getName()).isEqualTo("Continue[button]");
    }

    @Test
    public void testIncompleteWidgetObservationDoesNotClearExistingAttributes() throws IOException {
        TESTARDataWriter writer = createWriter();
        Page page = mockPage("Request Loan");

        WebORObject firstObject = writer.addWidgetObject(
            mockWidget(
                "username",
                "input",
                "text",
                "",
                "textbox",
                "textbox",
                "//input[@id='username']",
                "#username",
                "Username",
                "",
                "",
                "username"
            ),
            page
        );
        WebORObject secondObject = writer.addWidgetObject(
            mockWidget("username", "input", "text", "", "", "", "", "", "", "", "", ""),
            page
        );

        assertThat(secondObject).isSameAs(firstObject);
        assertThat(secondObject.getAttributeByName("xpath")).isEqualTo("//input[@id='username']");
        assertThat(secondObject.getAttributeByName("css")).isEqualTo("#username");
        assertThat(secondObject.getAttributeByName("Placeholder")).isEqualTo("Username");
        assertThat(secondObject.getAttributeByName("TestId")).isEqualTo("username");
    }

    @Test
    public void testAddAssertToExistingPageWithWidgets() throws IOException {
        TESTARDataWriter writer = createWriter();
        Page page = mockPage("Initial Page");
        PlaywrightState state = mockState(page);

        WebORObject widgetObject = writer.addWidgetObject(
            mockWidget(
                "Log In",
                "input",
                "submit",
                "",
                "",
                "",
                "//input[@type='submit']",
                "[type='submit']",
                "",
                "",
                "",
                ""
            ),
            page
        );

        ArtifactPersistenceResult persistenceResult = writer.addAssertTestStep(
            "Given the page is open",
            state,
            "Customer Login"
        );

        WebORPage webORPage = (WebORPage) widgetObject.getParent().getParent();

        assertThat(webORPage.getObjectGroups()).hasSize(2);
        assertThat(webORPage.getObjectGroupByName("assertCustomerL[text]")).isNotNull();
        assertThat(
                webORPage
                    .getObjectGroupByName("assertCustomerL[text]")
                    .getObjectByName("assertCustomerL[text]")
                    .getAttributeByName("Text")
            )
            .isEqualTo("Customer Login");
        assertThat(persistenceResult.getArtifacts())
            .extracting(ArtifactReference::getKind)
            .containsExactly("OBJECT_REPOSITORY_ASSERTION", "LOW_LEVEL_BDD_STEP");
        assertThat(persistenceResult.getArtifacts().get(0).getDisposition()).isEqualTo("created");
        assertThat(persistenceResult.getArtifacts().get(0).getObjectName())
            .isEqualTo("assertCustomerL[text]");
        assertThat(persistenceResult.getArtifacts().get(1).getAction())
            .isEqualTo("assertElementIsVisible");
        assertThat(persistenceResult.getArtifacts().get(1).getReusableTestCaseName())
            .isEqualTo("Given the page is open");

        Path pageFile = tempDir
            .resolve("ObjectRepository")
            .resolve("Web")
            .resolve("Initial Page.yaml");
        assertThat(Files.readString(pageFile)).contains("assertCustomerL[text]:", "Customer Login");
    }

    @Test
    public void testAddWidgetToPageThatAlreadyContainsAssert() throws IOException {
        TESTARDataWriter writer = createWriter();
        Page page = mockPage("Welcome Page");
        PlaywrightState state = mockState(page);

        writer.addAssertTestStep("Given the page is open", state, "Customer Login");
        WebORObject widgetObject = writer.addWidgetObject(
            mockWidget(
                "username",
                "input",
                "text",
                "",
                "",
                "",
                "//input[@type='text']",
                "[type='text']",
                "",
                "",
                "",
                ""
            ),
            page
        );

        WebORPage webORPage = (WebORPage) widgetObject.getParent().getParent();

        assertThat(webORPage.getObjectGroups()).hasSize(2);
        assertThat(webORPage.getObjectGroupByName("assertCustomerL[text]")).isNotNull();
        assertThat(webORPage.getObjectGroupByName("username[input]")).isNotNull();
    }

    @Test
    public void testInputsFromOneExecutionShareOneDataRow() throws IOException {
        InMemoryTestDataModel testDataModel = new InMemoryTestDataModel();
        TESTARDataWriter writer = createWriter(testDataModel);

        writer.addConcreteTestStep("username[input]", "", "Fill", "@user", "");
        writer.addConcreteTestStep("password[input]", "", "Fill", "@pass", "");

        assertThat(testDataModel.getRecords()).hasSize(1);
        Record record = testDataModel.getRecords().get(0);
        assertThat(record.get(testDataModel.getColumnIndex("username[input]_Fill")))
            .isEqualTo("user");
        assertThat(record.get(testDataModel.getColumnIndex("password[input]_Fill")))
            .isEqualTo("pass");
    }

    @Test
    public void testArtifactTraceIncludesDataValueButRedactsPassword() throws IOException {
        InMemoryTestDataModel testDataModel = new InMemoryTestDataModel();
        TESTARDataWriter writer = createWriter(testDataModel);
        Path traceFile = tempDir.resolve("trace.jsonl");
        AgentTraceContext traceContext = new AgentTraceContext(
            "data-run",
            new JsonlAgentTraceRecorder(traceFile)
        );

        ArtifactPersistenceResult amount = new ArtifactPersistenceResult();
        amount.add(
            writer.addAbstractTestStep(
                "When the user fills the loan amount",
                "amount[input]",
                "Fill amount",
                "Fill",
                "@999999",
                "Loan Request"
            )
        );
        AgentTraceSupport.appendArtifacts(
            traceContext,
            "When the user fills the loan amount",
            "Fill",
            amount
        );

        ArtifactPersistenceResult password = new ArtifactPersistenceResult();
        password.add(
            writer.addConcreteTestStep("password[input]", "Fill password", "Fill", "@demo", "Login")
        );
        AgentTraceSupport.appendArtifacts(traceContext, "", "Fill", password);
        traceContext.close();

        List<String> events = Files.readAllLines(traceFile);
        JsonNode amountArtifact = JSON_MAPPER.readTree(events.get(0)).path("artifacts");
        JsonNode passwordArtifact = JSON_MAPPER.readTree(events.get(1)).path("artifacts");
        assertThat(amountArtifact.path("testDataColumn").asText()).isEqualTo("amount[input]_Fill");
        assertThat(amountArtifact.path("testDataValue").asText()).isEqualTo("999999");
        assertThat(passwordArtifact.path("testDataValue").asText()).isEqualTo("[REDACTED]");
        assertThat(Files.readString(traceFile)).doesNotContain("demo");
        Record record = testDataModel.getRecords().get(0);
        assertThat(record.get(testDataModel.getColumnIndex("password[input]_Fill")))
            .isEqualTo("demo");
    }

    @Test
    public void testTitleAssertCreatesBrowserActionDataWithoutObjectRepositoryElement()
        throws IOException {
        InMemoryTestDataModel testDataModel = new InMemoryTestDataModel();
        TESTARDataWriter writer = createWriter(testDataModel);

        ArtifactPersistenceResult persistenceResult = writer.addTitleAssertTestStep(
            "Then the welcome page is shown",
            "ParaBank.*Welcome"
        );

        assertThat(testDataModel.getRecords()).hasSize(1);
        Record record = testDataModel.getRecords().get(0);
        assertThat(record.get(testDataModel.getColumnIndex("Browser_assertPageTitleMatches")))
            .isEqualTo("ParaBank.*Welcome");
        assertThat(tempDir.resolve("ObjectRepository").resolve("Web")).doesNotExist();
        assertThat(persistenceResult.getArtifacts()).hasSize(1);
        assertThat(persistenceResult.getArtifacts().get(0).getKind())
            .isEqualTo("LOW_LEVEL_BDD_STEP");
        assertThat(persistenceResult.getArtifacts().get(0).getAction())
            .isEqualTo("assertPageTitleMatches");
        assertThat(persistenceResult.getArtifacts().get(0).getObjectName()).isEqualTo("Browser");
        assertThat(persistenceResult.getArtifacts().get(0).getTestDataSheet())
            .isEqualTo(writer.getRunName());
        assertThat(persistenceResult.getArtifacts().get(0).getTestDataRow()).isEqualTo(1);
        assertThat(persistenceResult.getArtifacts().get(0).getTestDataColumn())
            .isEqualTo("Browser_assertPageTitleMatches");
        assertThat(persistenceResult.getArtifacts().get(0).getTestDataValue())
            .isEqualTo("ParaBank.*Welcome");
    }

    @Test
    public void testAssertTextsWithSamePrefixCreateDifferentObjects() throws IOException {
        TESTARDataWriter writer = createWriter();
        PlaywrightState state = mockState(mockPage("Loan Request"));

        writer.addAssertTestStep("Given the page is open", state, "Loan Request A");
        writer.addAssertTestStep("Then the result is shown", state, "Loan Request B");

        Path pageFile = tempDir
            .resolve("ObjectRepository")
            .resolve("Web")
            .resolve("Loan Request.yaml");
        assertThat(Files.readString(pageFile))
            .contains(
                "assertLoanReque[text]:",
                "assertLoanReque_2[text]:",
                "Loan Request A",
                "Loan Request B"
            );
    }

    @Test
    public void testProjectSavePreservesAssertionInObjectRepository() throws IOException {
        TESTARDataWriter writer = createWriter();
        Page page = mockPage("Loan Request");
        PlaywrightState state = mockState(page);
        writer.addWidgetObject(
            mockWidget(
                "Apply Now",
                "button",
                "",
                "",
                "button",
                "button",
                "",
                "#apply",
                "",
                "",
                "",
                ""
            ),
            page
        );

        writer.addAssertTestStep("Then the loan is denied", state, "Denied");
        writer.saveExecutionSteps();

        Path pageFile = tempDir.resolve("ObjectRepository/Web/Loan Request.yaml");
        assertThat(Files.readString(pageFile))
            .contains(
                "ApplyNow[button]:",
                "css: \"#apply\"",
                "assertDenied[text]:",
                "text: Denied"
            );
    }

    @Test
    public void testStateObservationTracesCreatedAndReusedObjects() throws IOException {
        TESTARDataWriter writer = createWriter();
        Path traceFile = tempDir.resolve("trace.jsonl");
        AgentTraceContext traceContext = new AgentTraceContext(
            "observation-run",
            new JsonlAgentTraceRecorder(traceFile)
        );
        INGeniousPersistenceService persistenceService = new INGeniousPersistenceService(
            writer,
            traceContext
        );
        Page page = mockPage("Loan Request");
        PlaywrightWidget widget = mockWidget(
            "Apply Now",
            "button",
            "",
            "",
            "button",
            "button",
            "",
            "#apply",
            "",
            "",
            "",
            ""
        );

        persistenceService.persistWidgetObject(widget, page);
        persistenceService.persistWidgetObject(widget, page);
        traceContext.close();

        List<String> events = Files.readAllLines(traceFile);
        assertThat(events).hasSize(2);
        JsonNode created = JSON_MAPPER.readTree(events.get(0));
        JsonNode reused = JSON_MAPPER.readTree(events.get(1));
        assertThat(created.path("type").asText()).isEqualTo("ARTIFACT_CREATED");
        assertThat(created.path("artifacts").path("kind").asText())
            .isEqualTo("OBJECT_REPOSITORY_OBJECT");
        assertThat(created.path("artifacts").path("objectName").asText())
            .isEqualTo("ApplyNow[button]");
        assertThat(reused.path("type").asText()).isEqualTo("ARTIFACT_REUSED");
        assertThat(reused.path("artifacts").path("objectName").asText())
            .isEqualTo("ApplyNow[button]");
    }

    private TESTARDataWriter createWriter() throws IOException {
        return createWriter(null);
    }

    private TESTARDataWriter createWriter(TestDataModel testDataModel) throws IOException {
        tempDir = Files.createTempDirectory("ingenious-testar-writer-test");
        Project project = mock(Project.class);
        Scenario bddScenario = mock(Scenario.class);
        Scenario reusableScenario = mock(Scenario.class);
        Map<String, TestCase> reusableTestCases = new HashMap<>();

        when(project.getName()).thenReturn("TestProject");
        when(project.getLocation()).thenReturn(tempDir.toString());
        when(project.addScenario("BDD-MCP_scenario")).thenReturn(bddScenario);
        when(project.getScenarioByName("BDD-MCP_scenario")).thenReturn(bddScenario);
        when(bddScenario.getName()).thenReturn("BDD-MCP_scenario");
        when(project.getReusableScenarioByName(anyString())).thenReturn(null);
        when(project.addReusableScenario(anyString())).thenReturn(reusableScenario);
        if (testDataModel != null) {
            EnvTestData envTestData = mock(EnvTestData.class);
            TestData defaultTestData = mock(TestData.class);
            when(project.getTestData()).thenReturn(envTestData);
            when(envTestData.defData()).thenReturn(defaultTestData);
            when(defaultTestData.getByName(anyString())).thenReturn(testDataModel);
        }
        when(bddScenario.addTestCase(anyString()))
            .thenAnswer(invocation -> createMockTestCase(invocation.getArgument(0, String.class)));
        when(reusableScenario.getTestCaseByName(anyString()))
            .thenAnswer(
                invocation -> reusableTestCases.get(invocation.getArgument(0, String.class))
            );
        when(reusableScenario.addTestCase(anyString()))
            .thenAnswer(
                invocation -> {
                    String testCaseName = invocation.getArgument(0, String.class);
                    TestCase testCase = createMockTestCase(testCaseName);
                    reusableTestCases.put(testCaseName, testCase);
                    return testCase;
                }
            );

        ObjectRepository objectRepository = new ObjectRepository(project);
        when(project.getObjectRepository()).thenReturn(objectRepository);
        doAnswer(
                invocation -> {
                    objectRepository.save();
                    return null;
                }
            )
            .when(project)
            .save();

        return new TESTARDataWriter(project, "Loan Scenario");
    }

    private static class InMemoryTestDataModel extends TestDataModel {

        private InMemoryTestDataModel() {
            super("", false);
            setColumns(Record.HEADERS);
        }

        @Override
        public Record getNewRecord() {
            return new Record();
        }

        @Override
        public Set<String> loadColumns(File location) {
            return Set.of(Record.HEADERS);
        }

        @Override
        public TestDataModel loadRecords(File location) {
            return this;
        }

        @Override
        public Set<String> loadColumns() {
            return Set.of(Record.HEADERS);
        }

        @Override
        public TestDataModel loadRecords() {
            return this;
        }

        @Override
        public Boolean rename(String newName) {
            return true;
        }

        @Override
        public Boolean delete() {
            return true;
        }

        @Override
        public void saveChanges() {}

        @Override
        public String getName() {
            return "InMemoryTestData";
        }
    }

    private TestCase createMockTestCase(String testCaseName) {
        TestCase testCase = mock(TestCase.class);
        List<TestStep> testSteps = new ArrayList<>();

        when(testCase.getName()).thenReturn(testCaseName);
        when(testCase.getTestSteps()).thenReturn(testSteps);
        when(testCase.addNewStep())
            .thenAnswer(
                invocation -> {
                    TestStep testStep = mock(TestStep.class);
                    testSteps.add(testStep);
                    return testStep;
                }
            );

        return testCase;
    }

    private Page mockPage(String title) {
        Page page = mock(Page.class);
        Locator locator = mock(Locator.class);

        when(page.title()).thenReturn(title);
        when(page.getByText(anyString(), any(Page.GetByTextOptions.class))).thenReturn(locator);
        when(page.getByLabel(anyString(), any(Page.GetByLabelOptions.class))).thenReturn(locator);
        when(locator.count()).thenReturn(1);

        return page;
    }

    private PlaywrightState mockState(Page page) {
        PlaywrightState state = mock(PlaywrightState.class);
        when(state.getPage()).thenReturn(page);
        return state;
    }

    private PlaywrightWidget mockWidget(
        String innerText,
        String tagName,
        String type,
        String locatorText,
        String role,
        String locatorRole,
        String xpath,
        String css,
        String placeholder,
        String altText,
        String title,
        String testId
    ) {
        PlaywrightWidget widget = mock(PlaywrightWidget.class);

        when(widget.get(PlaywrightTags.WebInnerText, "")).thenReturn(innerText);
        when(widget.get(PlaywrightTags.WebLocatorLabel, "")).thenReturn("");
        when(widget.get(PlaywrightTags.WebName, "")).thenReturn("");
        when(widget.get(PlaywrightTags.WebId, "")).thenReturn("");
        when(widget.get(PlaywrightTags.WebAriaLabel, "")).thenReturn("");
        when(widget.get(PlaywrightTags.WebPlaceholder, "")).thenReturn("");
        when(widget.get(PlaywrightTags.WebValue, "")).thenReturn("");
        when(widget.get(PlaywrightTags.WebTagName, "")).thenReturn(tagName);
        when(widget.get(PlaywrightTags.WebType, "")).thenReturn(type);

        when(widget.get(PlaywrightTags.WebLocatorText)).thenReturn(locatorText);
        when(widget.get(PlaywrightTags.WebLocatorLabel)).thenReturn("");
        when(widget.get(PlaywrightTags.WebLocatorRole)).thenReturn(locatorRole);
        when(widget.get(PlaywrightTags.WebLocatorXPath)).thenReturn(xpath);
        when(widget.get(PlaywrightTags.WebLocatorCSS)).thenReturn(css);
        when(widget.get(PlaywrightTags.WebLocatorPlaceholder)).thenReturn(placeholder);
        when(widget.get(PlaywrightTags.WebLocatorAltText)).thenReturn(altText);
        when(widget.get(PlaywrightTags.WebLocatorTitle)).thenReturn(title);
        when(widget.get(PlaywrightTags.WebLocatorTestId)).thenReturn(testId);
        when(widget.get(PlaywrightTags.WebRole)).thenReturn(role);

        return widget;
    }
}
