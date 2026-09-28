package com.ing.ide.main.testar.playwright.actions;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.mockito.ArgumentCaptor;
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
        doNothing().when(elementHandle).check(any(ElementHandle.CheckOptions.class));

        PlaywrightCheck action = createAction(elementHandle);

        action.run(null, null, 0);

        ArgumentCaptor<ElementHandle.CheckOptions> options = ArgumentCaptor.forClass(
            ElementHandle.CheckOptions.class
        );
        verify(elementHandle).check(options.capture());
        assertThat(options.getValue().timeout).isEqualTo(2000d);
        assertThat(options.getValue().force).isNull();
        verify(elementHandle, never()).check();
    }

    @Test
    public void retriesWithForceWhenNormalCheckIsIntercepted() throws ActionFailedException {
        ElementHandle elementHandle = mock(ElementHandle.class);
        AtomicBoolean checked = new AtomicBoolean(false);
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(
                invocation -> {
                    if (attempts.getAndIncrement() == 0) {
                        throw new PlaywrightException("intercepts pointer events");
                    }
                    checked.set(true);
                    return null;
                }
            )
            .when(elementHandle)
            .check(any(ElementHandle.CheckOptions.class));
        when(elementHandle.isChecked()).thenAnswer(invocation -> checked.get());

        PlaywrightCheck action = createAction(elementHandle);

        action.run(null, null, 0);

        ArgumentCaptor<ElementHandle.CheckOptions> options = ArgumentCaptor.forClass(
            ElementHandle.CheckOptions.class
        );
        verify(elementHandle, org.mockito.Mockito.times(2)).check(options.capture());
        List<ElementHandle.CheckOptions> capturedOptions = options.getAllValues();
        assertThat(capturedOptions.get(0).timeout).isEqualTo(2000d);
        assertThat(capturedOptions.get(0).force).isNull();
        assertThat(capturedOptions.get(1).timeout).isEqualTo(2000d);
        assertThat(capturedOptions.get(1).force).isTrue();
    }

    @Test
    public void failsWhenForcedCheckDoesNotSelectTheControl() {
        ElementHandle elementHandle = mock(ElementHandle.class);
        when(elementHandle.isChecked()).thenReturn(false, false, false);
        doAnswer(
                invocation -> {
                    throw new PlaywrightException("intercepts pointer events");
                }
            )
            .doNothing()
            .when(elementHandle)
            .check(any(ElementHandle.CheckOptions.class));

        PlaywrightCheck action = createAction(elementHandle);

        assertThatThrownBy(() -> action.run(null, null, 0))
            .isInstanceOf(ActionFailedException.class)
            .hasMessageContaining("was not checked");
    }

    @Test
    public void failsWhenForcedCheckThrows() {
        ElementHandle elementHandle = mock(ElementHandle.class);
        when(elementHandle.isChecked()).thenReturn(false);
        doThrow(new PlaywrightException("element detached"))
            .when(elementHandle)
            .check(any(ElementHandle.CheckOptions.class));

        PlaywrightCheck action = createAction(elementHandle);

        assertThatThrownBy(() -> action.run(null, null, 0))
            .isInstanceOf(ActionFailedException.class)
            .hasMessageContaining("Forced check failed");
    }

    private PlaywrightCheck createAction(ElementHandle elementHandle) {
        PlaywrightWidget widget = mock(PlaywrightWidget.class);
        when(widget.getElementHandle()).thenReturn(elementHandle);
        return new PlaywrightCheck(widget);
    }
}
