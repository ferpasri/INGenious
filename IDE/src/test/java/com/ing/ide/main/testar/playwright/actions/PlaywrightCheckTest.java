package com.ing.ide.main.testar.playwright.actions;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ing.ide.main.testar.playwright.system.PlaywrightWidget;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.PlaywrightException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.testar.monkey.alayer.exceptions.ActionFailedException;
import org.testng.annotations.Test;

public class PlaywrightCheckTest {

    @Test
    public void doesNotClickAnAlreadyCheckedControl() throws ActionFailedException {
        ElementHandle elementHandle = mock(ElementHandle.class);
        when(elementHandle.isChecked()).thenReturn(true);

        PlaywrightCheck action = createAction(elementHandle);

        action.run(null, null, 0);

        verify(elementHandle, never()).check();
        verify(elementHandle, never()).check(any(ElementHandle.CheckOptions.class));
    }

    @Test
    public void usesNormalCheckWhenTheControlIsActionable() throws ActionFailedException {
        ElementHandle elementHandle = mock(ElementHandle.class);
        when(elementHandle.isChecked()).thenReturn(false, true);
        doNothing().when(elementHandle).check();

        PlaywrightCheck action = createAction(elementHandle);

        action.run(null, null, 0);

        verify(elementHandle).check();
        verify(elementHandle, never()).check(any(ElementHandle.CheckOptions.class));
    }

    @Test
    public void retriesWithForceWhenNormalCheckIsIntercepted() throws ActionFailedException {
        ElementHandle elementHandle = mock(ElementHandle.class);
        AtomicBoolean checked = new AtomicBoolean(false);
        when(elementHandle.isChecked()).thenAnswer(invocation -> checked.get());
        doThrow(new PlaywrightException("intercepts pointer events")).when(elementHandle).check();
        doAnswer(
                invocation -> {
                    checked.set(true);
                    return null;
                }
            )
            .when(elementHandle)
            .check(any(ElementHandle.CheckOptions.class));

        PlaywrightCheck action = createAction(elementHandle);

        action.run(null, null, 0);

        verify(elementHandle).check();
        verify(elementHandle).check(any(ElementHandle.CheckOptions.class));
    }

    @Test
    public void failsWhenForcedCheckDoesNotSelectTheControl() {
        ElementHandle elementHandle = mock(ElementHandle.class);
        when(elementHandle.isChecked()).thenReturn(false, false, false);
        doThrow(new PlaywrightException("intercepts pointer events")).when(elementHandle).check();
        doNothing().when(elementHandle).check(any(ElementHandle.CheckOptions.class));

        PlaywrightCheck action = createAction(elementHandle);

        assertThatThrownBy(() -> action.run(null, null, 0))
            .isInstanceOf(ActionFailedException.class)
            .hasMessageContaining("was not checked");
    }

    private PlaywrightCheck createAction(ElementHandle elementHandle) {
        PlaywrightWidget widget = mock(PlaywrightWidget.class);
        when(widget.getElementHandle()).thenReturn(elementHandle);
        return new PlaywrightCheck(widget);
    }
}
