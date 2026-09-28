package com.ing.ide.main.testar.service.assertion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ing.ide.main.testar.TESTARDataWriter;
import com.ing.ide.main.testar.mcp.Feedback;
import com.ing.ide.main.testar.playwright.system.PlaywrightState;
import com.ing.ide.main.testar.service.SessionContext;
import com.ing.ide.main.testar.service.persistence.PersistenceService;
import com.microsoft.playwright.Page;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class PlaywrightAssertionServiceTest {
    private PersistenceService persistenceService;
    private PlaywrightAssertionService assertionService;
    private SessionContext context;

    @BeforeMethod
    public void setUp() {
        persistenceService = mock(PersistenceService.class);
        assertionService = new PlaywrightAssertionService(persistenceService);
        context = new SessionContext(mock(TESTARDataWriter.class));

        PlaywrightState state = mock(PlaywrightState.class);
        Page page = mock(Page.class);
        when(page.title()).thenReturn("ParaBank | Welcome");
        when(state.getPage()).thenReturn(page);
        context.setState(state);
    }

    @Test
    public void testAddTitleAssertPersistsMatchingTitlePattern() {
        Feedback feedback = assertionService.addTitleAssert(
            context,
            "Then the welcome page is shown",
            "ParaBank.*Welcome"
        );

        assertThat(feedback.isIssue()).isFalse();
        assertThat(feedback.toString()).isEqualTo("Page title assertion created successfully!");
        verify(persistenceService)
            .recordTitleAssertStep("Then the welcome page is shown", "ParaBank.*Welcome");
        verify(persistenceService).saveExecutionSteps();
    }

    @Test
    public void testAddTitleAssertRejectsInvalidPattern() {
        Feedback feedback = assertionService.addTitleAssert(
            context,
            "Then the welcome page is shown",
            "["
        );

        assertThat(feedback.isIssue()).isTrue();
        assertThat(feedback.getCode()).isEqualTo(Feedback.Code.ASSERT_TITLE_PATTERN_INVALID);
        verify(persistenceService, never())
            .recordTitleAssertStep("Then the welcome page is shown", "[");
        verify(persistenceService, never()).saveExecutionSteps();
    }

    @Test
    public void testAddTitleAssertRejectsPatternThatDoesNotMatchCurrentTitle() {
        Feedback feedback = assertionService.addTitleAssert(
            context,
            "Then the account page is shown",
            "Account Overview"
        );

        assertThat(feedback.isIssue()).isTrue();
        assertThat(feedback.getCode()).isEqualTo(Feedback.Code.ASSERT_TITLE_MISMATCH);
        verify(persistenceService, never())
            .recordTitleAssertStep("Then the account page is shown", "Account Overview");
        verify(persistenceService, never()).saveExecutionSteps();
    }
}
