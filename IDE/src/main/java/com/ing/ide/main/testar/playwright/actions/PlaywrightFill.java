package com.ing.ide.main.testar.playwright.actions;

import com.ing.ide.main.testar.playwright.system.PlaywrightWidget;
import com.microsoft.playwright.ElementHandle;
import org.testar.monkey.alayer.*;
import org.testar.monkey.alayer.exceptions.ActionFailedException;

public class PlaywrightFill extends TaggableBase implements Action {
    private final ElementHandle elementHandle;
    private final String typedText;

    public PlaywrightFill(PlaywrightWidget widget, String typedText) {
        this.elementHandle = widget.getElementHandle();
        this.typedText = typedText;

        // TODO: Change by a configurable abstraction mechanism
        this.set(Tags.AbstractID, "PlaywrightFill:" + widget.get(Tags.AbstractID));
        this.set(Tags.ConcreteID, "PlaywrightFill:" + widget.get(Tags.ConcreteID));

        this.set(Tags.Desc, toString());
        this.set(Tags.OriginWidget, widget);
    }

    public String getTypedText() {
        return typedText;
    }

    @Override
    public void run(SUT system, State state, double duration) throws ActionFailedException {
        // Fill the web element with the desired text
        elementHandle.fill(typedText);
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
            "PlaywrightFill text '%s' into element: [id='%s', name='%s', text='%s']",
            typedText,
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
