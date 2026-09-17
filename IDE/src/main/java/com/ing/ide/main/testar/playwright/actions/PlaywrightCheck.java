package com.ing.ide.main.testar.playwright.actions;

import com.ing.ide.main.testar.playwright.system.PlaywrightWidget;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.PlaywrightException;
import org.testar.monkey.alayer.Action;
import org.testar.monkey.alayer.Role;
import org.testar.monkey.alayer.SUT;
import org.testar.monkey.alayer.State;
import org.testar.monkey.alayer.TaggableBase;
import org.testar.monkey.alayer.Tags;
import org.testar.monkey.alayer.exceptions.ActionFailedException;

public class PlaywrightCheck extends TaggableBase implements Action {
    private final ElementHandle elementHandle;

    public PlaywrightCheck(PlaywrightWidget widget) {
        this.elementHandle = widget.getElementHandle();

        // TODO: Change by a configurable abstraction mechanism
        this.set(Tags.AbstractID, "PlaywrightClick:" + widget.get(Tags.AbstractID));
        this.set(Tags.ConcreteID, "PlaywrightClick:" + widget.get(Tags.ConcreteID));

        this.set(Tags.Desc, toString());
        this.set(Tags.OriginWidget, widget);
    }

    @Override
    public void run(SUT system, State state, double duration) throws ActionFailedException {
        if (elementHandle.isChecked()) {
            return;
        }

        try {
            elementHandle.check();
        } catch (PlaywrightException normalCheckFailure) {
            elementHandle.check(new ElementHandle.CheckOptions().setForce(true));
        }

        if (!elementHandle.isChecked()) {
            throw new ActionFailedException("The radio or checkbox was not checked.");
        }
    }

    @Override
    public String toString() {
        String id = elementHandle.getAttribute("id") != null
            ? elementHandle.getAttribute("id")
            : "";
        String name = elementHandle.getAttribute("name") != null
            ? elementHandle.getAttribute("name")
            : "";
        String value = elementHandle.getAttribute("value") != null
            ? elementHandle.getAttribute("value")
            : "";

        return String.format("PlaywrightCheck: [id='%s', name='%s', value='%s']", id, name, value);
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
