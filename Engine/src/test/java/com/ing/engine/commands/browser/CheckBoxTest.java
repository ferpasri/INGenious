package com.ing.engine.commands.browser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ing.engine.core.CommandControl;
import com.ing.engine.execution.data.UserDataAccess;
import com.ing.engine.reporting.TestCaseReport;
import com.ing.ingenious.api.status.Status;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.PlaywrightException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.annotations.Test;

public class CheckBoxTest {

    @Test
    public void usesTheBoundedNormalCheckWhenTheControlIsActionable() {
        Locator locator = mock(Locator.class);
        TestCaseReport report = mock(TestCaseReport.class);
        when(locator.isEnabled()).thenReturn(true);
        when(locator.isChecked()).thenReturn(false, true);
        doNothing().when(locator).check(any(Locator.CheckOptions.class));

        CheckBox checkBox = createCheckBox(locator, report);

        checkBox.Check();

        ArgumentCaptor<Locator.CheckOptions> options = ArgumentCaptor.forClass(
            Locator.CheckOptions.class
        );
        verify(locator).check(options.capture());
        assertThat(options.getValue().timeout).isEqualTo(2000d);
        assertThat(options.getValue().force).isNull();
        verify(locator, never()).check();
        verify(report).updateTestLog(eq("check"), contains("successfully"), eq(Status.DONE));
    }

    @Test
    public void fallsBackToForceAfterNormalCheckIsNotActionable() {
        Locator locator = mock(Locator.class);
        TestCaseReport report = mock(TestCaseReport.class);
        AtomicBoolean checked = new AtomicBoolean(false);
        when(locator.isEnabled()).thenReturn(true);
        when(locator.isChecked()).thenAnswer(invocation -> checked.get());
        doAnswer(
                invocation -> {
                    if (checked.compareAndSet(false, true)) {
                        throw new PlaywrightException("intercepts pointer events");
                    }
                    return null;
                }
            )
            .when(locator)
            .check(any(Locator.CheckOptions.class));

        CheckBox checkBox = createCheckBox(locator, report);

        checkBox.Check();

        ArgumentCaptor<Locator.CheckOptions> options = ArgumentCaptor.forClass(
            Locator.CheckOptions.class
        );
        verify(locator, Mockito.times(2)).check(options.capture());
        List<Locator.CheckOptions> capturedOptions = options.getAllValues();
        assertThat(capturedOptions.get(0).timeout).isEqualTo(2000d);
        assertThat(capturedOptions.get(0).force).isNull();
        assertThat(capturedOptions.get(1).timeout).isEqualTo(2000d);
        assertThat(capturedOptions.get(1).force).isTrue();
        verify(report, never())
            .updateTestLog(eq("check"), contains("retrying with force"), any(Status.class));
        verify(report).updateTestLog(eq("check"), contains("successfully"), eq(Status.DONE));
    }

    @Test
    public void doesNotInteractWithAnAlreadyCheckedControl() {
        Locator locator = mock(Locator.class);
        TestCaseReport report = mock(TestCaseReport.class);
        when(locator.isEnabled()).thenReturn(true);
        when(locator.isChecked()).thenReturn(true);

        CheckBox checkBox = createCheckBox(locator, report);

        checkBox.Check();

        verify(locator, never()).check();
        verify(locator, never()).check(any(Locator.CheckOptions.class));
        verify(report).updateTestLog(eq("check"), contains("successfully"), eq(Status.DONE));
    }

    @Test
    public void reportsFailureWhenForcedCheckThrows() {
        Locator locator = mock(Locator.class);
        TestCaseReport report = mock(TestCaseReport.class);
        when(locator.isEnabled()).thenReturn(true);
        when(locator.isChecked()).thenReturn(false);
        doThrow(new PlaywrightException("element detached"))
            .when(locator)
            .check(any(Locator.CheckOptions.class));

        CheckBox checkBox = createCheckBox(locator, report);

        checkBox.Check();

        verify(report, never())
            .updateTestLog(eq("check"), contains("retrying with force"), any(Status.class));
        verify(report).updateTestLog(eq("check"), contains("Forced check failed"), eq(Status.FAIL));
    }

    @Test
    public void reportsFailureWhenForcedCheckDoesNotSelectTheControl() {
        Locator locator = mock(Locator.class);
        TestCaseReport report = mock(TestCaseReport.class);
        when(locator.isEnabled()).thenReturn(true);
        when(locator.isChecked()).thenReturn(false);
        doThrow(new PlaywrightException("intercepts pointer events"))
            .doNothing()
            .when(locator)
            .check(any(Locator.CheckOptions.class));

        CheckBox checkBox = createCheckBox(locator, report);

        checkBox.Check();

        verify(report)
            .updateTestLog(eq("check"), contains("couldn't be selected/checked"), eq(Status.FAIL));
    }

    private CheckBox createCheckBox(Locator locator, TestCaseReport report) {
        CommandControl commandControl = mock(CommandControl.class);
        commandControl.webDriver = mock(com.ing.engine.drivers.WebDriverCreation.class);
        commandControl.Report = report;
        commandControl.userData = mock(UserDataAccess.class);
        when(commandControl.userData.getScenario()).thenReturn("Scenario");
        when(commandControl.userData.getTestCase()).thenReturn("TestCase");

        CheckBox checkBox = new CheckBox(commandControl);
        checkBox.Locator = locator;
        checkBox.ObjectName = "radio";
        checkBox.Action = "Check";
        return checkBox;
    }
}
