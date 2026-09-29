package com.ing.ide.main.testar.trace;

import java.util.LinkedHashMap;
import java.util.Map;

public class ArtifactReference {
    private String kind = "";
    private String disposition = "";
    private String scenarioName = "";
    private String testCaseName = "";
    private String reusableScenarioName = "";
    private String reusableTestCaseName = "";
    private int lowLevelStepIndex = -1;
    private String action = "";
    private String inputReference = "";
    private String pageName = "";
    private String objectGroupName = "";
    private String objectName = "";
    private String testDataSheet = "";
    private int testDataRow = -1;
    private String testDataColumn = "";
    private String testDataValue = "";
    private Map<String, String> locatorProperties = new LinkedHashMap<>();

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = valueOrEmpty(kind);
    }

    public String getDisposition() {
        return disposition;
    }

    public void setDisposition(String disposition) {
        this.disposition = valueOrEmpty(disposition);
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public void setScenarioName(String scenarioName) {
        this.scenarioName = valueOrEmpty(scenarioName);
    }

    public String getTestCaseName() {
        return testCaseName;
    }

    public void setTestCaseName(String testCaseName) {
        this.testCaseName = valueOrEmpty(testCaseName);
    }

    public String getReusableScenarioName() {
        return reusableScenarioName;
    }

    public void setReusableScenarioName(String reusableScenarioName) {
        this.reusableScenarioName = valueOrEmpty(reusableScenarioName);
    }

    public String getReusableTestCaseName() {
        return reusableTestCaseName;
    }

    public void setReusableTestCaseName(String reusableTestCaseName) {
        this.reusableTestCaseName = valueOrEmpty(reusableTestCaseName);
    }

    public int getLowLevelStepIndex() {
        return lowLevelStepIndex;
    }

    public void setLowLevelStepIndex(int lowLevelStepIndex) {
        this.lowLevelStepIndex = lowLevelStepIndex;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = valueOrEmpty(action);
    }

    public String getInputReference() {
        return inputReference;
    }

    public void setInputReference(String inputReference) {
        this.inputReference = valueOrEmpty(inputReference);
    }

    public String getPageName() {
        return pageName;
    }

    public void setPageName(String pageName) {
        this.pageName = valueOrEmpty(pageName);
    }

    public String getObjectGroupName() {
        return objectGroupName;
    }

    public void setObjectGroupName(String objectGroupName) {
        this.objectGroupName = valueOrEmpty(objectGroupName);
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = valueOrEmpty(objectName);
    }

    public String getTestDataSheet() {
        return testDataSheet;
    }

    public void setTestDataSheet(String testDataSheet) {
        this.testDataSheet = valueOrEmpty(testDataSheet);
    }

    public int getTestDataRow() {
        return testDataRow;
    }

    public void setTestDataRow(int testDataRow) {
        this.testDataRow = testDataRow;
    }

    public String getTestDataColumn() {
        return testDataColumn;
    }

    public void setTestDataColumn(String testDataColumn) {
        this.testDataColumn = valueOrEmpty(testDataColumn);
    }

    public String getTestDataValue() {
        return testDataValue;
    }

    public void setTestDataValue(String testDataValue) {
        this.testDataValue = valueOrEmpty(testDataValue);
    }

    public Map<String, String> getLocatorProperties() {
        return locatorProperties;
    }

    public void setLocatorProperties(Map<String, String> locatorProperties) {
        this.locatorProperties =
            locatorProperties != null ? locatorProperties : new LinkedHashMap<>();
    }

    private String valueOrEmpty(String value) {
        return value != null ? value : "";
    }
}
