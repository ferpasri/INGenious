package com.ing.ide.main.testar.playwright.actions;

import com.ing.ide.main.testar.playwright.system.PlaywrightState;
import com.microsoft.playwright.Page;
import org.testar.monkey.alayer.*;
import org.testar.monkey.alayer.exceptions.ActionFailedException;

public class PlaywrightRefresh extends TaggableBase implements Action {
    private final Page page;

    public PlaywrightRefresh(PlaywrightState state) {
        this.page = state.getPage();

        // TODO: Change by a configurable abstraction mechanism
        this.set(Tags.AbstractID, "PlaywrightRefresh:" + state.get(Tags.AbstractID));
        this.set(Tags.ConcreteID, "PlaywrightRefresh:" + state.get(Tags.ConcreteID));

        this.set(Tags.Desc, toString());
        this.set(Tags.OriginWidget, state);
    }

    @Override
    public void run(SUT system, State state, double duration) throws ActionFailedException {
        page.reload();
    }

    @Override
    public String toString() {
        return String.format("PlaywrightRefresh state");
    }

    @Override
    public String toShortString() {
        return toString();
    }

    @Override
    public String toParametersString() {
        return toShortString();
    }

    @Override
    public String toString(Role... discardParameters) {
        return toShortString();
    }
}
