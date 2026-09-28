package com.ing.ide.main.testar.playwright.system;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.testar.monkey.alayer.Tag;
import org.testar.monkey.alayer.TagsBase;

public class PlaywrightTags extends TagsBase {
    protected static final Set<Tag<?>> wdTags = new HashSet<>();

    private PlaywrightTags() {}

    public static final Tag<String> WebId = from("WebId", String.class);

    public static final Tag<String> WebName = from("WebName", String.class);

    public static final Tag<String> WebTagName = from("WebTagName", String.class);

    public static final Tag<String> WebValue = from("WebValue", String.class);

    public static final Tag<String> WebTextContent = from("WebTextContent", String.class);

    public static final Tag<String> WebPlaceholder = from("WebPlaceholder", String.class);

    public static final Tag<String> WebAriaLabel = from("WebAriaLabel", String.class);

    public static final Tag<String> WebRole = from("WebRole", String.class);

    public static final Tag<String> WebHref = from("WebHref", String.class);

    public static final Tag<String> WebType = from("WebType", String.class);

    public static final Tag<String> WebTitle = from("WebTitle", String.class);

    public static final Tag<String> WebInnerText = from("WebInnerText", String.class);

    /** Locator Tags **/

    public static final Tag<String> WebLocatorRole = from("WebLocatorRole", String.class);

    public static final Tag<String> WebLocatorXPath = from("WebLocatorXPath", String.class);

    public static final Tag<String> WebLocatorText = from("WebLocatorText", String.class);

    public static final Tag<String> WebLocatorCSS = from("WebLocatorCSS", String.class);

    public static final Tag<String> WebLocatorPlaceholder = from(
        "WebLocatorPlaceholder",
        String.class
    );

    public static final Tag<String> WebLocatorLabel = from("WebLocatorLabel", String.class);

    public static final Tag<String> WebLocatorAltText = from("WebLocatorAltText", String.class);

    public static final Tag<String> WebLocatorTitle = from("WebLocatorTitle", String.class);

    public static final Tag<String> WebLocatorTestId = from("WebLocatorTestId", String.class);

    public static final Tag<String> WebChainedLocator = from("WebChainedLocator", String.class);

    /** Custom Widget and Locators helper Tags **/

    public static final Tag<Boolean> WebIsVisible = from("WebIsVisible", Boolean.class);

    public static final Tag<Boolean> WebIsModal = from("WebIsModal", Boolean.class);

    /** Action Tags **/

    public static final Tag<Boolean> WebIsClickable = from("WebIsClickable", Boolean.class);

    public static final Tag<Boolean> WebIsFillable = from("WebIsFillable", Boolean.class);

    public static final Tag<Boolean> WebIsChecked = from("WebIsChecked", Boolean.class);

    public static Set<Tag<?>> getAllTags() {
        return tagSet;
    }

    protected static <T> Tag<T> from(String name, Class<T> valueType) {
        Tag<T> tag = TagsBase.from(name, valueType);
        wdTags.add(tag);
        return tag;
    }

    public static Set<Tag<?>> getPlaywrightTags() {
        return Collections.unmodifiableSet(wdTags);
    }
}
