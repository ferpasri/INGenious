package com.ing.ide.main.testar.playwright.system;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import java.io.Serializable;
import java.util.*;
import org.testar.monkey.Drag;
import org.testar.monkey.Util;
import org.testar.monkey.alayer.*;

public class PlaywrightWidget implements Widget, Serializable {
    PlaywrightState root;
    PlaywrightWidget parent;
    Map<Tag<?>, Object> tags = new HashMap<>();
    List<PlaywrightWidget> children = new ArrayList<>();

    private ElementHandle elementHandle;

    // Define selectors for clickable elements
    protected final String[] clickableSelectors = {
        "a", // Links
        "button", // Buttons
        "input[type='button']", // Input button
        "input[type='submit']", // Input submit button
        "input[type='reset']", // Input reset button
        "input[type='checkbox']", // Checkbox inputs
        "input[type='radio']", // Radio button inputs
        "[onclick]", // Elements with onclick attributes (custom clickable elements)
        "[role='button']" // Elements with a role attribute as buttons (often used in modern UIs)
    };

    // Define selectors for fillable elements
    protected final String[] fillableSelectors = {
        "input[type='text']", // Text input fields
        "textarea", // Text areas
        "input[class='input']", // Input fields
        "input[type='email']", // Email input fields
        "input[type='password']" // Password input fields
    };

    // Define selectors for selectable elements
    protected final String[] selectableSelectors = {
        "select" // Dropdowns
    };

    protected PlaywrightWidget(PlaywrightState state, PlaywrightWidget parent) {
        this.parent = parent;
        this.root = state;

        if (parent != null) {
            root.connect(parent, this);
        }
    }

    public PlaywrightWidget(
        PlaywrightState state,
        PlaywrightWidget parent,
        ElementHandle elementHandle
    ) {
        this(state, parent);
        this.elementHandle = elementHandle;
        Page statePage = state.getPage();
        this.set(
                Tags.Shape,
                Rect.fromCoordinates(
                    0,
                    0,
                    statePage.viewportSize().width,
                    statePage.viewportSize().height
                )
            );
        this.set(Tags.Role, Roles.Widget);

        loadAttributes(elementHandle);
        loadLocators(elementHandle);
    }

    public ElementHandle getElementHandle() {
        return elementHandle;
    }

    private void loadAttributes(ElementHandle element) {
        // Evaluate a JavaScript function inside the browser context to extract multiple attributes
        Object result = element.evaluate(
            "(el, selectors) => {" +
            // Unpack clickable and fillable selectors
            "  const [clickable, fillable] = selectors;" +
            // Custom function to check visibility
            "  const style = window.getComputedStyle(el);" +
            "  const hasBox = !!(el.offsetWidth || el.offsetHeight || el.getClientRects().length);" +
            "  let visible = false;" +
            "  if (style && hasBox && style.visibility !== 'hidden' && style.display !== 'none' && parseFloat(style.opacity || '1') !== 0) {" +
            "    visible = true;" +
            "    let p = el.parentElement;" +
            "    while (p && visible) {" +
            "      const ps = window.getComputedStyle(p);" +
            "      if (ps.display === 'none' || ps.visibility === 'hidden') visible = false;" +
            "      p = p.parentElement;" +
            "    }" +
            "  }" +
            // Return the collected attributes
            "  return {" +
            "    id: el.id || ''," +
            "    name: el.name || ''," +
            "    tagName: (el.tagName || '').toLowerCase()," +
            "    textContent: el.textContent || ''," +
            "    value: el.value || ''," +
            "    placeholder: el.getAttribute('placeholder') || ''," +
            "    ariaLabel: el.getAttribute('aria-label') || ''," +
            "    role: el.getAttribute('role') || ''," +
            "    href: el.getAttribute('href') || ''," +
            "    type: el.getAttribute('type') || ''," +
            "    title: el.getAttribute('title') || ''," +
            "    innerText: el.innerText || ''," +
            "    isClickable: el.matches(clickable)," +
            "    isFillable: el.matches(fillable)," +
            "    isVisible: visible" +
            "  };" +
            "}",
            Arrays.asList(
                String.join(", ", clickableSelectors),
                String.join(", ", fillableSelectors)
            )
        );

        Map<String, Object> attributes = (Map<String, Object>) result;

        this.set(PlaywrightTags.WebId, (String) attributes.get("id"));
        this.set(PlaywrightTags.WebName, (String) attributes.get("name"));
        this.set(PlaywrightTags.WebTagName, (String) attributes.get("tagName"));
        this.set(PlaywrightTags.WebValue, (String) attributes.get("value"));
        this.set(PlaywrightTags.WebTextContent, (String) attributes.get("textContent"));
        this.set(PlaywrightTags.WebPlaceholder, (String) attributes.get("placeholder"));
        this.set(PlaywrightTags.WebAriaLabel, (String) attributes.get("ariaLabel"));
        this.set(PlaywrightTags.WebRole, (String) attributes.get("role"));
        this.set(PlaywrightTags.WebHref, (String) attributes.get("href"));
        this.set(PlaywrightTags.WebType, (String) attributes.get("type"));
        this.set(PlaywrightTags.WebTitle, (String) attributes.get("title"));
        this.set(PlaywrightTags.WebInnerText, (String) attributes.get("innerText"));
        this.set(PlaywrightTags.WebIsVisible, (Boolean) attributes.get("isVisible"));

        this.set(PlaywrightTags.WebIsClickable, (Boolean) attributes.get("isClickable"));
        this.set(PlaywrightTags.WebIsFillable, (Boolean) attributes.get("isFillable"));

        // TODO: Change by a configurable abstraction mechanism
        this.set(Tags.AbstractID, attributes.get("id") + "_" + attributes.get("name"));
        this.set(
                Tags.ConcreteID,
                attributes.get("id") +
                "_" +
                attributes.get("name") +
                "_" +
                attributes.get("textContent")
            );
    }

    private void loadLocators(ElementHandle element) {
        Object result = element.evaluate("el => window.__locatorUtils.extractLocators(el)");

        Map<String, Object> selectors = (Map<String, Object>) result;

        this.set(PlaywrightTags.WebLocatorRole, (String) selectors.get("role"));
        this.set(PlaywrightTags.WebLocatorXPath, (String) selectors.get("xpath"));
        this.set(PlaywrightTags.WebLocatorText, (String) selectors.get("text"));
        this.set(PlaywrightTags.WebLocatorCSS, (String) selectors.get("css"));
        this.set(PlaywrightTags.WebLocatorPlaceholder, (String) selectors.get("placeholder"));
        this.set(PlaywrightTags.WebLocatorLabel, (String) selectors.get("label"));
        this.set(PlaywrightTags.WebLocatorAltText, (String) selectors.get("altText"));
        this.set(PlaywrightTags.WebLocatorTitle, (String) selectors.get("title"));
        this.set(PlaywrightTags.WebLocatorTestId, (String) selectors.get("testId"));

        Object isModalObj = selectors.get("isModal");
        if (isModalObj instanceof Boolean) {
            this.set(PlaywrightTags.WebIsModal, (Boolean) isModalObj);
        } else {
            this.set(PlaywrightTags.WebIsModal, false);
        }

        // TODO: Prepare a logic to Compose a chained selector
        this.set(PlaywrightTags.WebChainedLocator, "");
    }

    public final void moveTo(Widget p, int idx) {
        root.setParent(this, p, idx);
    }

    public final PlaywrightWidget addChild() {
        return root.addChild(this);
    }

    public final PlaywrightState root() {
        return root;
    }

    public final PlaywrightWidget parent() {
        return root.getParent(this);
    }

    public final PlaywrightWidget child(int i) {
        return root.getChild(this, i);
    }

    public final void remove() {
        root.remove(this);
    }

    public final int childCount() {
        return root.childCount(this);
    }

    public final <T> T get(Tag<T> tag) {
        return root.get(this, tag);
    }

    public final <T> void set(Tag<T> tag, T value) {
        root.setTag(this, tag, value);
    }

    public final <T> T get(Tag<T> tag, T defaultValue) {
        return root.get(this, tag, defaultValue);
    }

    public final Iterable<Tag<?>> tags() {
        return root.tags(this);
    }

    public final void remove(Tag<?> tag) {
        root.remove(this, tag);
    }

    public String getRepresentation(String tab) {
        return "COMPLETE: PlaywrightWidget getRepresentation";
    }

    @Override
    public String toString(Tag<?>... tags) {
        return Util.widgetDesc(this, tags);
    }

    @Override
    public Drag[] scrollDrags(double scrollArrowSize, double scrollThick) {
        return null;
    }
}
