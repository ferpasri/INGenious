package com.ing.ide.main.testar.service;

import com.ing.ide.main.testar.TESTARDataWriter;
import com.ing.ide.main.testar.playwright.system.PlaywrightSUT;
import com.ing.ide.main.testar.playwright.system.PlaywrightState;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SessionContext {

    private final TESTARDataWriter dataWriter;
    private final List<String> executedActions = new ArrayList<>();
    private final List<ExecutedActionEntry> executedActionEntries = new ArrayList<>();

    private PlaywrightSUT system;
    private PlaywrightState state;
    private String bddScenarioName = "";
    private String bddScenarioSource = "";
    private Path runtimeTempDir;
    private int stateImageCounter;

    public SessionContext(TESTARDataWriter dataWriter) {
        this.dataWriter = dataWriter;
    }

    public TESTARDataWriter getDataWriter() {
        return dataWriter;
    }

    public List<String> getExecutedActions() {
        return executedActions;
    }

    public List<ExecutedActionEntry> getExecutedActionEntries() {
        return executedActionEntries;
    }

    public PlaywrightSUT getSystem() {
        return system;
    }

    public void setSystem(PlaywrightSUT system) {
        this.system = system;
    }

    public PlaywrightState getState() {
        return state;
    }

    public void setState(PlaywrightState state) {
        this.state = state;
    }

    public String getBddScenarioName() {
        return bddScenarioName;
    }

    public void setBddScenarioName(String bddScenarioName) {
        this.bddScenarioName = bddScenarioName;
    }

    public String getBddScenarioSource() {
        return bddScenarioSource;
    }

    public void setBddScenarioSource(String bddScenarioSource) {
        this.bddScenarioSource = bddScenarioSource;
    }

    public Path getRuntimeTempDir() {
        return runtimeTempDir;
    }

    public void setRuntimeTempDir(Path runtimeTempDir) {
        this.runtimeTempDir = runtimeTempDir;
    }

    public int nextStateImageCounter() {
        stateImageCounter++;
        return stateImageCounter;
    }

    public static final class ExecutedActionEntry {

        private final String bddStep;
        private final String actionType;
        private final String selector;
        private final String value;
        private final String description;

        public ExecutedActionEntry(String bddStep, String actionType, String selector, String value, String description) {
            this.bddStep = bddStep;
            this.actionType = actionType;
            this.selector = selector;
            this.value = value;
            this.description = description;
        }

        public String getBddStep() {
            return bddStep;
        }

        public String getActionType() {
            return actionType;
        }

        public String getSelector() {
            return selector;
        }

        public String getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }
    }
}
