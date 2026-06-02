package com.ing.ide.main.testar.playwright.system;

import com.ing.ide.main.utils.Utils;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import org.testar.monkey.alayer.SUTBase;
import org.testar.monkey.alayer.exceptions.SystemStopException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlaywrightSUT extends SUTBase {

    private static final Logger LOG = Logger.getLogger(PlaywrightSUT.class.getName());

    private final Browser browser;
    private final BrowserContext browserContext;
    private final Page page;

    public PlaywrightSUT(String webSUT) {
        Playwright playwright = Playwright.create();

        this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
        this.browserContext = browser.newContext();
        loadLocatorScripts();

        this.page = browserContext.newPage();
        page.navigate(webSUT); // Navigate to the webSUT URL
        this.page.waitForLoadState(LoadState.LOAD, new Page.WaitForLoadStateOptions().setTimeout(20000));
        this.page.setDefaultTimeout(2000); // 2 seconds of timeout
    }

    public Browser getBrowser() {
        return browser;
    }

    public Page getPage() {
        return page;
    }

    private void loadLocatorScripts(){
        try {
            String testarResourcesPath = Utils.getAppRoot() + File.separator + "testar" + File.separator;
            String cssJavaScript = testarResourcesPath + "css-selector-generator.min.js";
            String locatorsJavaScript = testarResourcesPath + "locatorUtils.js";
            browserContext.addInitScript(Files.readString(Paths.get(cssJavaScript)));
            browserContext.addInitScript(Files.readString(Paths.get(locatorsJavaScript)));
        } catch (IOException ioe) {
            LOG.log(Level.SEVERE, "PlaywrightSUT: Failed to load the locator JavaScript files", ioe);
        }
    }

    @Override
    public void stop() throws SystemStopException {
        browserContext.close();
    }

    @Override
    public boolean isRunning() {
        return browserContext.pages().size() == 0;
    }

    @Override
    public String getStatus() {
        // Gather details of open pages
        StringBuilder pageDetails = new StringBuilder();
        for (Page page : browserContext.pages()) {
            String title = page.title();
            pageDetails.append(String.format(" | Title: %s ", title != null ? title : "Untitled"));
        }
        return "PlaywrightSUT status: " + pageDetails;
    }

    @Override
    public void setNativeAutomationCache() {

    }
}
