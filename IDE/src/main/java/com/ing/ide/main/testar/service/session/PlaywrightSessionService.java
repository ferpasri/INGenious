package com.ing.ide.main.testar.service.session;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.ing.ide.main.testar.mcp.Feedback;
import com.ing.ide.main.testar.playwright.system.PlaywrightSUT;
import com.ing.ide.main.testar.playwright.system.PlaywrightState;
import com.ing.ide.main.testar.service.SessionContext;
import com.ing.ide.main.testar.service.action.PlaywrightActionService;
import com.ing.ide.main.testar.service.persistence.PersistenceService;
import com.microsoft.playwright.Response;

public class PlaywrightSessionService implements SessionService {

    private final PersistenceService persistenceService;

    public PlaywrightSessionService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Override
    public Feedback loadWebURL(SessionContext context, String bddStep, String url) {
        try {
            PlaywrightSUT system = new PlaywrightSUT(url);
            context.setSystem(system);
            context.setState(new PlaywrightState(system));
        } catch (Exception e) {
            addSevereLog("Failed to run PlaywrightSUT with URL: " + url);
            addSevereLog(e.getMessage());
            return Feedback.issue(Feedback.Code.LOAD_URL_FAILED, "Loading the Web URL: " + e.getMessage());
        }

        // Add browser control test step into INGenious
        persistenceService.recordLoadWebUrl(bddStep, url);

        return Feedback.validContext("Web URL loaded successfully!");
    }

    @Override
    public Feedback getCurrentURL(SessionContext context) {
        if (context.getState() == null) {
            return Feedback.issue(Feedback.Code.STATE_NOT_INITIALIZED, "No web state-page initialized.");
        }

        String url = context.getState().getPage().url();
        if (url == null || url.isEmpty()) {
            return Feedback.issue(Feedback.Code.URL_NOT_AVAILABLE, "No web url available.");
        }

        return Feedback.validContext(url);
    }

    @Override
    public Feedback navigateBack(SessionContext context) {
        if (context.getState() == null) {
            return Feedback.issue(Feedback.Code.STATE_NOT_INITIALIZED, "No web state-page initialized.");
        }

        Response response = context.getState().getPage().goBack();
        if (response == null) {
            return Feedback.issue(Feedback.Code.NAVIGATION_BACK_UNAVAILABLE, "Cannot navigate back - no previous page.");
        }

        // Add browser control test step into INGenious
        persistenceService.recordNavigateBack();

        return Feedback.validContext(String.format("Success navigating back to '%s'", response.url()));
    }

    @Override
    public void stop(SessionContext context) {
        // Then, close the playwright session
        if (context.getSystem() == null) {
            addInfoLog("stopTestExecution called without an active Playwright session.");
            return;
        }

        try {
            context.getSystem().stop();
        } catch (Exception e) {
            addSevereLog("Failed to stop Playwright session: " + e.getMessage());
        } finally {
            context.setSystem(null);
            context.setState(null);
        }
    }

    private void addInfoLog(String msg) {
        Logger.getLogger(PlaywrightActionService.class.getName()).log(Level.INFO, msg);
    }

    private void addSevereLog(String msg) {
        Logger.getLogger(PlaywrightActionService.class.getName()).log(Level.SEVERE, msg);
    }
}
