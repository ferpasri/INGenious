package com.ing.ide.main.testar.playwright.actions;

import com.ing.ide.main.testar.playwright.system.PlaywrightWidget;
import com.microsoft.playwright.ElementHandle;
import org.testar.monkey.alayer.*;
import org.testar.monkey.alayer.exceptions.ActionFailedException;

public class PlaywrightClick extends TaggableBase implements Action {
    private final ElementHandle elementHandle;

    public PlaywrightClick(PlaywrightWidget widget) {
        this.elementHandle = widget.getElementHandle();

        // TODO: Change by a configurable abstraction mechanism
        this.set(Tags.AbstractID, "PlaywrightClick:" + widget.get(Tags.AbstractID));
        this.set(Tags.ConcreteID, "PlaywrightClick:" + widget.get(Tags.ConcreteID));

        this.set(Tags.Desc, toString());
        this.set(Tags.OriginWidget, widget);
    }

    @Override
    public void run(SUT system, State state, double duration) throws ActionFailedException {
        // Click the web element
        elementHandle.click();
    }

    @Override
    public String toString() {
        // Retrieve the id, name, text content, and href attributes of the element
        String id = elementHandle.getAttribute("id") != null
            ? elementHandle.getAttribute("id")
            : "";
        String name = elementHandle.getAttribute("name") != null
            ? elementHandle.getAttribute("name")
            : "";
        String textContent = elementHandle.textContent() != null ? elementHandle.textContent() : "";
        String href = elementHandle.getAttribute("href") != null
            ? elementHandle.getAttribute("href")
            : "";

        // Build and return the formatted string
        return String.format(
            "PlaywrightClick: [id='%s', name='%s', text='%s', href='%s']",
            id,
            name,
            textContent,
            href
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
