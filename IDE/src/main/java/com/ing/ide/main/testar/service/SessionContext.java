package com.ing.ide.main.testar.service;

import com.ing.ide.main.testar.TESTARDataWriter;
import com.ing.ide.main.testar.playwright.system.PlaywrightSUT;
import com.ing.ide.main.testar.playwright.system.PlaywrightState;

import java.util.ArrayList;
import java.util.List;

public class SessionContext {

    private final TESTARDataWriter dataWriter;
    private final List<String> executedActions = new ArrayList<>();

    private PlaywrightSUT system;
    private PlaywrightState state;

    public SessionContext(TESTARDataWriter dataWriter) {
        this.dataWriter = dataWriter;
    }

    public TESTARDataWriter getDataWriter() {
        return dataWriter;
    }

    public List<String> getExecutedActions() {
        return executedActions;
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
}
