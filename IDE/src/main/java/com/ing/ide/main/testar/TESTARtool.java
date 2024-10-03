package com.ing.ide.main.testar;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.RandomStringUtils;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ing.ide.main.testar.actions.TESTARAction;
import com.ing.ide.main.testar.actions.TESTARActionClick;
import com.ing.ide.main.testar.actions.TESTARActionFill;
import com.ing.ide.main.testar.reporting.TESTARHtmlReport;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;

public class TESTARtool {
	private static final Logger logger = LogManager.getLogger();

	private final String webSUT;
	private final int numberActions;
	private final String filterPattern;
	private final String suspiciousPattern;
	private final Map<String, String> triggerActionsMap;

	public TESTARtool(String webSUT, Map<String, String> triggerActionsMap, int numberActions, String filterPattern, String suspiciousPattern) {
		this.webSUT = webSUT;
		this.triggerActionsMap = triggerActionsMap;
		this.numberActions = numberActions;
		this.filterPattern = filterPattern;
		this.suspiciousPattern = suspiciousPattern;
	}

	public String generateSequence() {
		// Initialize a TESTAR HTML report
		TESTARHtmlReport htmlReport = new TESTARHtmlReport();

		// Initialize Playwright and launch Chromium
		Playwright playwright = Playwright.create();
		Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
		Page page = browser.newPage();
		page.setDefaultTimeout(3000); // 3 seconds of timeout

		try {
			// Navigate to the webSUT URL
			page.navigate(webSUT);

			// Initial wait
			page.waitForTimeout(2000);

			// Wait for the page to finish loading 
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
			page.waitForLoadState(LoadState.NETWORKIDLE);
			page.waitForLoadState(LoadState.LOAD);

			// Trigger initial actions
			triggerInitialActions(page, triggerActionsMap);

			// Apply test oracles into the initial state
			String verdict = getVerdict(page);
			// If the test verdict is not OK, return the erroneous verdict
			if(!verdict.equals("OK")) {
				return verdict;
			}

			for(int i = 0; i < numberActions; i++) {
				// Add an image of the state in the HTML report
				htmlReport.addState(page.screenshot(), page.url());

				// Derive TESTAR available actions to interact with web elements
				List<TESTARAction> actions = deriveActions(page);

				// Control the web page by checking the state contains available actions
				actions = controlWebPage(page, actions);

				// Write available derived actions in the HTML report
				htmlReport.addDerivedActions(actions);

				// Select one of available actions
				TESTARAction selectedAction = selectRandomAction(actions);
				htmlReport.addSelectedAction(selectedAction);

				// Execute the selected action
				executeAction(page, selectedAction);

				// Apply test oracles into the state
				verdict = getVerdict(page);
				// If the test verdict is not OK, return the erroneous verdict
				if(!verdict.equals("OK")) {
					return verdict;
				}
			}

			// Add an image of the final state in the HTML report
			htmlReport.addState(page.screenshot(), page.url());

		} catch(Exception e) {
			logger.log(Level.ERROR, e.getMessage());
			return "Error occurred: " + e.getMessage();
		} finally {
			browser.close();
		}

		return "OK";
	}

	private boolean triggerInitialActions(Page page, Map<String, String> triggerActionsMap) {
		try {
			// Iterate over the selector-value pairs
			for (Map.Entry<String, String> entry : triggerActionsMap.entrySet()) {
				String selector = entry.getKey();
				String value = entry.getValue();

				// Wait for each field to appear
				page.waitForSelector(selector);

				// Then, click or fill if value exists
				if(value.isEmpty()) page.click(selector);
				else page.fill(selector, value);
			}

			// Static wait
			page.waitForTimeout(300);

			// Wait for the page to finish loading 
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
			page.waitForLoadState(LoadState.NETWORKIDLE);
			page.waitForLoadState(LoadState.LOAD);

			return true;
		} catch (Exception e) {
			logger.log(Level.ERROR, "Trigger Initial Actions failed: " + e.getMessage());
			return false;
		}
	}

	private String getVerdict(Page page) {
		// Define the regex pattern to search for "error" or "exception" (case-insensitive)
		Pattern errorPattern = Pattern.compile(suspiciousPattern, Pattern.CASE_INSENSITIVE);

		// Query all relevant elements that might contain error messages
		List<ElementHandle> messageElements = page.querySelectorAll("div, span, p, h1, h2, h3");

		// Iterate through the elements to check for error messages
		for (ElementHandle element : messageElements) {
			String textContent;
			try {
				// Get the text content of the element
				textContent = element.textContent();
			} catch (Exception e) {
				// If there's an issue fetching the innerText, skip this element
				continue;
			}

			// Check if the text matches the error pattern
			if (errorPattern.matcher(textContent).find()) {
				// Return the found error message
				return "Error found: " + textContent.trim();
			}
		}

		// If no error messages were found, return "OK"
		return "OK";
	}

	private List<TESTARAction> deriveActions(Page page) {
		// Define selectors for clickable elements
		String[] clickableSelectors = {
				"a",                       // Links
				"button",                  // Buttons
				"input[type='button']",    // Input button
				"input[type='submit']",    // Input submit button
				"input[type='reset']",     // Input reset button
				"input[type='checkbox']",  // Checkbox inputs
				"input[type='radio']",     // Radio button inputs
				"select",                  // Dropdowns
				"[onclick]",               // Elements with onclick attributes (custom clickable elements)
				"[role='button']"          // Elements with a role attribute as buttons (often used in modern UIs)
		};

		// Define selectors for fillable elements
		String[] fillableSelectors = {
				"input[type='text']",      // Text input fields
				"textarea",                // Text areas
				"input[class='input']",    // Input fields
				"input[type='email']",     // Email input fields
				"input[type='password']"   // Password input fields
		};

		// List all clickable elements considering all clickable selectors into a single CSS selector string
		List<ElementHandle> clickableElements = page.querySelectorAll(String.join(", ", clickableSelectors));

		// List all fillable elements considering all fillable selectors into a single CSS selector string
		List<ElementHandle> fillableElements = page.querySelectorAll(String.join(", ", fillableSelectors));

		// Compile the filter pattern into a regex
		Pattern pattern = Pattern.compile(filterPattern);

		// Create click actions for non filtered clickable elements
		List<TESTARAction> clickActions = clickableElements.stream()
				.filter(element -> {
					String elementContent;
					try {
						// Get the content of the element
						elementContent = element.textContent() + (element.getAttribute("href") != null ? element.getAttribute("href") : "");
					} catch (Exception e) {
						// If there's an issue fetching the text, treat it as non-matching
						return true; // Keep it in the list if we can't retrieve its text
					}
					// Return true if the element does not match the filter pattern
					return !pattern.matcher(elementContent).find();
				})
				// Create a TESTARActionClick for each non filtered clickable element
				.map(element -> new TESTARActionClick(element))
				.collect(Collectors.toList());

		// Create fill actions for non filtered fillable elements
		List<TESTARAction> fillActions = fillableElements.stream()
				.filter(element -> {
					String elementContent;
					try {
						// Get the content of the element
						elementContent = element.textContent();
					} catch (Exception e) {
						return true; // Keep it in the list if we can't retrieve its text
					}
					return !pattern.matcher(elementContent).find();
				})
				// Create a TESTARActionFill for each non filtered fillable element
				// The text to type is generated randomly
				.map(element -> new TESTARActionFill(element, RandomStringUtils.randomAlphabetic(10)))
				.collect(Collectors.toList());

		// Return the combined list of actions
		return Stream.concat(clickActions.stream(), fillActions.stream())
				.collect(Collectors.toList());
	}

	//TODO: Derive a TESTARActionBack instead of navigate to the initial state
	private List<TESTARAction> controlWebPage(Page page, List<TESTARAction> actions){
		// If TESTAR was not able to derive any action,
		// maybe because the DOM state does not contain interactive elements
		// Or if TESTAR is not in the webSUT URL
		if(actions.isEmpty() || !page.url().contains(webSUT)) {
			// Navigate to the original webSUT URL
			page.navigate(webSUT);
			// Wait for the page to finish loading 
			page.waitForTimeout(300);
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
			page.waitForLoadState(LoadState.NETWORKIDLE);
			page.waitForLoadState(LoadState.LOAD);
			// And derive again the available actions
			actions = deriveActions(page);
		}
		return actions;
	}

	private TESTARAction selectRandomAction(List<TESTARAction> actions) {
		// Select a random action from the available actions
		return actions.get(new Random().nextInt(actions.size()));
	}

	private boolean executeAction(Page page, TESTARAction action) {
		try {
			action.run(page);
		} catch(Exception e) {
			logger.log(Level.ERROR, e.getMessage());
			return false;
		}
		return true;
	}

}
