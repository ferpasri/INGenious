package com.ing.ide.main.testar.service.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ing.ide.main.testar.TESTARDataWriter;
import com.ing.ide.main.testar.mcp.Feedback;
import com.ing.ide.main.testar.playwright.system.PlaywrightSUT;
import com.ing.ide.main.testar.playwright.system.PlaywrightState;
import com.ing.ide.main.testar.playwright.system.PlaywrightTags;
import com.ing.ide.main.testar.playwright.system.PlaywrightWidget;
import com.ing.ide.main.testar.service.SessionContext;
import com.ing.ide.main.testar.service.persistence.PersistenceService;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import org.mockito.InOrder;
import org.testng.annotations.Test;

public class PlaywrightActionServiceTest {

    @Test
    public void persistsClickOnlyAfterTheClickSucceeds() {
        PersistenceService persistenceService = mock(PersistenceService.class);
        PlaywrightActionService actionService = new PlaywrightActionService(persistenceService);
        ElementHandle elementHandle = mock(ElementHandle.class);
        PlaywrightWidget widget = mockWidget(elementHandle, "button");
        SessionContext context = createContext(widget, "#submit");
        doNothing().when(elementHandle).click();

        Feedback feedback = actionService.executeClickAction(context, "When submit", "#submit");

        assertThat(feedback.isIssue()).isFalse();
        InOrder order = inOrder(elementHandle, persistenceService);
        order.verify(elementHandle).click();
        order
            .verify(persistenceService)
            .recordActionStep(
                eq("When submit"),
                any(PlaywrightState.class),
                any(),
                any(Page.class)
            );
    }

    @Test
    public void doesNotPersistClickWhenTheClickFails() {
        PersistenceService persistenceService = mock(PersistenceService.class);
        PlaywrightActionService actionService = new PlaywrightActionService(persistenceService);
        ElementHandle elementHandle = mock(ElementHandle.class);
        PlaywrightWidget widget = mockWidget(elementHandle, "button");
        SessionContext context = createContext(widget, "#submit");
        doThrow(new PlaywrightException("covered")).when(elementHandle).click();

        Feedback feedback = actionService.executeClickAction(context, "When submit", "#submit");

        assertThat(feedback.isIssue()).isTrue();
        verify(persistenceService, never())
            .recordActionStep(anyString(), any(PlaywrightState.class), any(), any(Page.class));
    }

    @Test
    public void executesAndPersistsACheckedRadio() {
        PersistenceService persistenceService = mock(PersistenceService.class);
        PlaywrightActionService actionService = new PlaywrightActionService(persistenceService);
        ElementHandle elementHandle = mock(ElementHandle.class);
        PlaywrightWidget widget = mockWidget(elementHandle, "radio");
        SessionContext context = createContext(widget, "input[name='hasExpenses'][value='false']");
        when(elementHandle.isChecked()).thenReturn(false, true);
        doNothing().when(elementHandle).check();

        Feedback feedback = actionService.executeCheckAction(
            context,
            "And the user does not have any debts",
            "input[name='hasExpenses'][value='false']"
        );

        assertThat(feedback.isIssue()).isFalse();
        verify(persistenceService)
            .recordActionStep(
                eq("And the user does not have any debts"),
                any(PlaywrightState.class),
                any(),
                any(Page.class)
            );
    }

    @Test
    public void doesNotPersistRadioWhenNormalAndForcedChecksFail() {
        PersistenceService persistenceService = mock(PersistenceService.class);
        PlaywrightActionService actionService = new PlaywrightActionService(persistenceService);
        ElementHandle elementHandle = mock(ElementHandle.class);
        PlaywrightWidget widget = mockWidget(elementHandle, "radio");
        SessionContext context = createContext(widget, "#radio");
        when(elementHandle.isChecked()).thenReturn(false, false, false);
        doThrow(new PlaywrightException("intercepts pointer events")).when(elementHandle).check();
        doNothing().when(elementHandle).check(any(ElementHandle.CheckOptions.class));

        Feedback feedback = actionService.executeCheckAction(context, "When radio", "#radio");

        assertThat(feedback.isIssue()).isTrue();
        verify(persistenceService, never())
            .recordActionStep(anyString(), any(PlaywrightState.class), any(), any(Page.class));
    }

    private SessionContext createContext(PlaywrightWidget widget, String selector) {
        SessionContext context = new SessionContext(mock(TESTARDataWriter.class));
        PlaywrightState state = mock(PlaywrightState.class);
        when(state.getWidgetFromCssSelector(selector)).thenReturn(widget);
        when(state.getPage()).thenReturn(mock(Page.class));
        context.setState(state);
        context.setSystem(mock(PlaywrightSUT.class));
        return context;
    }

    private PlaywrightWidget mockWidget(ElementHandle elementHandle, String type) {
        PlaywrightWidget widget = mock(PlaywrightWidget.class);
        when(widget.getElementHandle()).thenReturn(elementHandle);
        when(widget.get(eq(PlaywrightTags.WebType), anyString())).thenReturn(type);
        return widget;
    }
}
