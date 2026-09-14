package com.ing.ide.main.testar.playwright.actions;

import com.ing.ide.main.testar.playwright.system.PlaywrightWidget;
import com.microsoft.playwright.ElementHandle;
import java.util.List;
import org.testar.monkey.alayer.*;
import org.testar.monkey.alayer.exceptions.ActionFailedException;

public class PlaywrightSelect extends TaggableBase implements Action {
    private final ElementHandle elementHandle;
    private final String optionValue;

    public PlaywrightSelect(PlaywrightWidget widget, String optionValue) {
        this.elementHandle = widget.getElementHandle();
        this.optionValue = optionValue;

        // TODO: Change by a configurable abstraction mechanism
        this.set(Tags.AbstractID, "PlaywrightSelect:" + widget.get(Tags.AbstractID));
        this.set(Tags.ConcreteID, "PlaywrightSelect:" + widget.get(Tags.ConcreteID));

        this.set(Tags.Desc, toString());
        this.set(Tags.OriginWidget, widget);
    }

    public String getOptionValue() {
        return optionValue;
    }

    @Override
    public void run(SUT system, State state, double duration) throws ActionFailedException {
        // Select the desired option value of a select dropdown element
        elementHandle.selectOption(optionValue);
    }

    @Override
    public String toString() {
        // Retrieve the id, name, and text content attributes of the element
        String id = elementHandle.getAttribute("id") != null
            ? elementHandle.getAttribute("id")
            : "";
        String name = elementHandle.getAttribute("name") != null
            ? elementHandle.getAttribute("name")
            : "";
        String textContent = elementHandle.textContent() != null ? elementHandle.textContent() : "";

        // Build and return the formatted string
        return String.format(
            "PlaywrightSelect value '%s' in element: [id='%s', name='%s', text='%s']",
            optionValue,
            id,
            name,
            textContent
        );
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
