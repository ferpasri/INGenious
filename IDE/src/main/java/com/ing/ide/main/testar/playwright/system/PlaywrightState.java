package com.ing.ide.main.testar.playwright.system;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import java.util.*;
import java.util.stream.Stream;
import org.testar.StateManagementTags;
import org.testar.monkey.Assert;
import org.testar.monkey.Util;
import org.testar.monkey.alayer.*;
import org.testar.monkey.alayer.exceptions.NoSuchTagException;

public class PlaywrightState extends PlaywrightWidget implements State {
    private final Page page;

    public PlaywrightState(PlaywrightSUT system) {
        super(null, null);
        this.root = this;
        this.page = system.getPage();

        // TODO: Change by a configurable abstraction mechanism
        this.set(Tags.AbstractID, page.title());
        this.set(Tags.ConcreteID, page.url());
    }

    public Page getPage() {
        return page;
    }

    public List<PlaywrightWidget> getInteractiveWidgets() {
        List<PlaywrightWidget> stateWidgets = new ArrayList<>();

        String widgetsSelector = String.join(
            ", ",
            Stream
                .of(
                    Arrays.stream(clickableSelectors),
                    Arrays.stream(fillableSelectors),
                    Arrays.stream(selectableSelectors)
                )
                .flatMap(s -> s)
                .toArray(String[]::new)
        );

        List<ElementHandle> stateElements = this.page.querySelectorAll(widgetsSelector);

        for (ElementHandle elementHandle : stateElements) {
            PlaywrightWidget widget = new PlaywrightWidget(this, this, elementHandle);
            stateWidgets.add(widget);
        }

        return stateWidgets;
    }

    public List<PlaywrightWidget> getVisibleWidgetsWithText() {
        List<PlaywrightWidget> stateWidgets = new ArrayList<>();

        List<ElementHandle> stateElements = this.page.querySelectorAll("*");

        for (ElementHandle elementHandle : stateElements) {
            PlaywrightWidget widget = new PlaywrightWidget(this, this, elementHandle);

            // Custom visible function used to check element visibility
            boolean isVisible = widget.get(PlaywrightTags.WebIsVisible, false);
            // Custom getText locator function used to direct text nodes
            String text = widget.get(PlaywrightTags.WebLocatorText, "");

            if (isVisible && text != null && !text.trim().isEmpty()) {
                stateWidgets.add(widget);
            }
        }

        return stateWidgets;
    }

    public PlaywrightWidget getWidgetFromCssSelector(String cssSelector) {
        ElementHandle elementHandle = this.page.querySelector(cssSelector);
        if (elementHandle != null) return new PlaywrightWidget(
            this,
            this,
            elementHandle
        ); else return null;
    }

    public byte[] getScreenshot() {
        return this.page.screenshot(new Page.ScreenshotOptions().setFullPage(true));
    }

    @Override
    public Iterator<Widget> iterator() {
        return new WidgetIterator(this);
    }

    public void remove(PlaywrightWidget w) {
        Assert.isTrue(this != w, "You cannot remove the root!");
        assert (w.parent != null);
        w.parent.children.remove(w);
        invalidateChildren(w);
    }

    public void invalidateChildren(PlaywrightWidget w) {
        w.root = null;
        for (PlaywrightWidget c : w.children) {
            invalidateChildren(c);
        }
    }

    public void setParent(PlaywrightWidget w, Widget parent, int idx) {
        Assert.notNull(parent);
        Assert.isTrue(parent instanceof PlaywrightWidget);
        Assert.isTrue(w != this, "You cannot set the root's parent!");
        assert (w.parent != null);

        PlaywrightWidget webParent = (PlaywrightWidget) parent;
        Assert.isTrue(webParent.root == this);
        Assert.isTrue(!Util.isAncestorOf(w, parent), "The parent is a descendent of this widget!");

        w.parent.children.remove(w);
        webParent.children.add(idx, w);
        w.parent = webParent;
    }

    PlaywrightWidget addChild(PlaywrightWidget parent) {
        return new PlaywrightWidget(this, parent);
    }

    void connect(PlaywrightWidget parent, PlaywrightWidget child) {
        parent.children.add(child);
    }

    public <T> T get(PlaywrightWidget w, Tag<T> t) {
        T ret = get(w, t, null);
        if (ret == null) {
            throw new NoSuchTagException(t);
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(PlaywrightWidget w, Tag<T> t, T defaultValue) {
        Tag<T> stateManagementTag = StateManagementTags.getMappedTag(t);
        if (stateManagementTag != null) {
            t = stateManagementTag;
        }

        Object ret = w.tags.get(t);

        if (ret != null) {
            return (T) ret;
        } else if (w.tags.containsKey(t)) {
            return defaultValue;
        }

        cacheTag(w, t, ret);

        return (ret == null) ? defaultValue : (T) ret;
    }

    @SuppressWarnings("unchecked")
    public <T> T cacheTag(PlaywrightWidget w, Tag<T> t, Object value) {
        w.tags.put(t, value);
        return (T) value;
    }

    public <T> void setTag(PlaywrightWidget w, Tag<T> t, T value) {
        Assert.notNull(value);
        w.tags.put(t, value);
    }

    public <T> void remove(PlaywrightWidget w, Tag<T> t) {
        Assert.notNull(w, t);
        w.tags.put(t, null);
    }

    public PlaywrightWidget getChild(PlaywrightWidget w, int idx) {
        return w.children.get(idx);
    }

    public int childCount(PlaywrightWidget w) {
        return w.children.size();
    }

    public PlaywrightWidget getParent(PlaywrightWidget w) {
        return w.parent;
    }

    Iterable<Tag<?>> tags(final PlaywrightWidget w) {
        Assert.notNull(w);

        // compile a query set
        final Set<Tag<?>> queryTags = new HashSet<>();
        queryTags.addAll(tags.keySet());
        queryTags.addAll(Tags.tagSet());

        Iterable<Tag<?>> ret = () ->
            new Iterator<>() {
                final Iterator<Tag<?>> i = queryTags.iterator();
                final PlaywrightWidget target = w;
                Tag<?> next;

                private Tag<?> fetchNext() {
                    if (next == null) {
                        while (i.hasNext()) {
                            next = i.next();
                            if (target.get(next, null) != null) {
                                return next;
                            }
                        }
                        next = null;
                    }
                    return next;
                }

                public boolean hasNext() {
                    return fetchNext() != null;
                }

                public Tag<?> next() {
                    Tag<?> ret1 = fetchNext();
                    if (ret1 == null) {
                        throw new NoSuchElementException();
                    }
                    next = null;
                    return ret1;
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        return ret;
    }

    public String toString() {
        return Util.treeDesc(this, 2, Tags.Role, Tags.Title);
    }
}
