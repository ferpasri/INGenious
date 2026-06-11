package com.ing.ide.main.testar.service.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ing.ide.main.testar.TESTARDataWriter;
import com.ing.ide.main.testar.mcp.Feedback;
import com.ing.ide.main.testar.playwright.system.PlaywrightState;
import com.ing.ide.main.testar.playwright.system.PlaywrightTags;
import com.ing.ide.main.testar.playwright.system.PlaywrightWidget;
import com.ing.ide.main.testar.service.SessionContext;
import com.ing.ide.main.testar.service.persistence.PersistenceService;
import com.microsoft.playwright.Page;
import java.util.List;
import org.mockito.MockedConstruction;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class PlaywrightStateServiceTest {

    private PersistenceService persistenceService;
    private PlaywrightStateService stateService;
    private SessionContext context;

    @BeforeMethod
    public void setUp() {
        persistenceService = mock(PersistenceService.class);
        stateService = new PlaywrightStateService(persistenceService);
        context = new SessionContext(mock(TESTARDataWriter.class));
        context.setState(mock(PlaywrightState.class));
    }

    @Test
    public void testStatePageIsNotInitialized() {
        context.setState(null);

        Feedback feedback = stateService.getStateInteractiveWidgets(context);

        assertThat(feedback.isIssue()).isTrue();
        assertThat(feedback.toString()).contains("No web state-page initialized.");
    }

    @Test
    public void testStateOnlyPersistsEligibleWidgets() {
        PlaywrightWidget eligibleWidget = mockInteractiveWidget(
                "#username",
                "",
                "input",
                "Username",
                "Username",
                "username"
        );
        PlaywrightWidget widgetWithoutCss = mockInteractiveWidget(
                "",
                "",
                "input",
                "Ignored",
                "Ignored",
                "ignored"
        );
        PlaywrightWidget externalLinkWidget = mockInteractiveWidget(
                "#external-help",
                "https://example.com/help",
                "a",
                "Help",
                "Help",
                "help"
        );

        Page page = mock(Page.class);
        when(page.url()).thenReturn("https://para.testar.org");

        try (MockedConstruction<PlaywrightState> ignored = mockConstruction(
                PlaywrightState.class,
                (stateMock, constructionContext) -> {
                    when(stateMock.getPage()).thenReturn(page);
                    when(stateMock.getInteractiveWidgets()).thenReturn(List.of(
                            eligibleWidget,
                            widgetWithoutCss,
                            externalLinkWidget
                    ));
                })) {

            Feedback feedback = stateService.getStateInteractiveWidgets(context);

            assertThat(feedback.isIssue()).isFalse();
            assertThat(feedback.toString()).contains("css: #username");
            assertThat(feedback.toString()).contains("text: Username");
            assertThat(feedback.toString()).doesNotContain("#external-help");
            assertThat(feedback.toString()).doesNotContain("Ignored");

            verify(persistenceService, times(1)).persistWidgetObject(eligibleWidget, page);
        }
    }

    @Test
    public void testStateVisualTextPersistsWidgets() {
        PlaywrightWidget eligibleWidget = mockInteractiveWidget(
                "#loan-amount",
                "",
                "input",
                "Loan Amount",
                "Loan Amount",
                "loanAmount"
        );
        PlaywrightWidget visibleTextWidget = mockVisibleTextWidget("Denied");

        Page page = mock(Page.class);
        when(page.url()).thenReturn("https://para.testar.org");

        try (MockedConstruction<PlaywrightState> ignored = mockConstruction(
                PlaywrightState.class,
                (stateMock, constructionContext) -> {
                    when(stateMock.getPage()).thenReturn(page);
                    when(stateMock.getInteractiveWidgets()).thenReturn(List.of(eligibleWidget));
                    when(stateMock.getVisibleWidgetsWithText()).thenReturn(List.of(visibleTextWidget));
                })) {

            Feedback feedback = stateService.getStateVisualText(context);

            assertThat(feedback.isIssue()).isFalse();
            assertThat(feedback.toString()).isEqualTo("text: Denied");

            verify(persistenceService, times(1)).persistWidgetObject(eligibleWidget, page);
        }
    }

    private PlaywrightWidget mockInteractiveWidget(
            String cssSelector,
            String href,
            String tagName,
            String locatorText,
            String innerText,
            String fieldName
    ) {
        PlaywrightWidget widget = mock(PlaywrightWidget.class);

        when(widget.get(PlaywrightTags.WebLocatorCSS)).thenReturn(cssSelector);
        when(widget.get(eq(PlaywrightTags.WebLocatorCSS), anyString())).thenReturn(cssSelector);

        when(widget.get(PlaywrightTags.WebHref)).thenReturn(href);
        when(widget.get(eq(PlaywrightTags.WebHref), anyString())).thenReturn(href);

        when(widget.get(PlaywrightTags.WebTagName)).thenReturn(tagName);
        when(widget.get(eq(PlaywrightTags.WebTagName), anyString())).thenReturn(tagName);

        when(widget.get(PlaywrightTags.WebLocatorText)).thenReturn(locatorText);
        when(widget.get(eq(PlaywrightTags.WebLocatorText), anyString())).thenReturn(locatorText);

        when(widget.get(PlaywrightTags.WebLocatorPlaceholder)).thenReturn("");
        when(widget.get(eq(PlaywrightTags.WebLocatorPlaceholder), anyString())).thenReturn("");

        when(widget.get(PlaywrightTags.WebLocatorLabel)).thenReturn("");
        when(widget.get(eq(PlaywrightTags.WebLocatorLabel), anyString())).thenReturn("");

        when(widget.get(PlaywrightTags.WebLocatorAltText)).thenReturn("");
        when(widget.get(eq(PlaywrightTags.WebLocatorAltText), anyString())).thenReturn("");

        when(widget.get(eq(PlaywrightTags.WebIsModal), eq(false))).thenReturn(false);
        when(widget.get(eq(PlaywrightTags.WebInnerText), anyString())).thenReturn(innerText);
        when(widget.get(eq(PlaywrightTags.WebName), anyString())).thenReturn(fieldName);
        when(widget.get(eq(PlaywrightTags.WebId), anyString())).thenReturn(fieldName);
        when(widget.get(eq(PlaywrightTags.WebAriaLabel), anyString())).thenReturn("");
        when(widget.get(eq(PlaywrightTags.WebPlaceholder), anyString())).thenReturn("");
        when(widget.get(eq(PlaywrightTags.WebValue), anyString())).thenReturn("");

        return widget;
    }

    private PlaywrightWidget mockVisibleTextWidget(String text) {
        PlaywrightWidget widget = mock(PlaywrightWidget.class);
        when(widget.get(PlaywrightTags.WebLocatorText)).thenReturn(text);
        when(widget.get(eq(PlaywrightTags.WebLocatorText), anyString())).thenReturn(text);
        return widget;
    }
}
