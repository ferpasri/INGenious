package com.ing.ide.main.testar;

import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

	public TESTARtool(String webSUT, int numberActions, String filterPattern, String suspiciousPattern) {
		this.webSUT = webSUT;
		this.numberActions = numberActions;
		this.filterPattern = filterPattern;
		this.suspiciousPattern = suspiciousPattern;
	}

	public String generateSequence() {
		// Initialize Playwright and launch Chromium
		Playwright playwright = Playwright.create();
		Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
		Page page = browser.newPage();

		try {
			// Navigate to the webSUT URL
			page.navigate(webSUT);

			// Initial wait
			page.waitForTimeout(3000);

			// Wait for the page to finish loading 
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
			page.waitForLoadState(LoadState.NETWORKIDLE);
			page.waitForLoadState(LoadState.LOAD);

			// Apply test oracles into the initial state
			String verdict = getVerdict(page);
			// If the test verdict is not OK, return the erroneous verdict
			if(!verdict.equals("OK")) {
				return verdict;
			}

			for(int i = 0; i < numberActions; i++) {

				// Derive interactive elements
				List<ElementHandle> interactiveElements = deriveInteractiveElements(page);

				// Control the web page by checking the state contains interactive elements
				interactiveElements = controlWebPage(page, interactiveElements);

				// Select one interactive element
				ElementHandle selectedElement = selectRandomElement(interactiveElements);

				// Execute the selected interactive element
				executeAction(page, selectedElement);

				// Apply test oracles into the state
				verdict = getVerdict(page);
				// If the test verdict is not OK, return the erroneous verdict
				if(!verdict.equals("OK")) {
					return verdict;
				}
			}

		} catch(Exception e) {
			logger.log(Level.ERROR, e.getMessage());
			return "Error occurred: " + e.getMessage();
		} finally {
			browser.close();
		}

		return "OK";
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

	private List<ElementHandle> deriveInteractiveElements(Page page) {
		// Define the selectors for actionable elements
		String[] selectors = {
				"a",                // Links
				"button",           // Buttons
				"input",            // Input fields (text, checkbox, radio, etc.)
				"select",           // Dropdowns
				"[onclick]",        // Elements with onclick attributes (custom clickable elements)
				"[role='button']"   // Elements with a role attribute as buttons (often used in modern UIs)
		};

		// Combine all the selectors into a single CSS selector string
		String allSelectors = String.join(", ", selectors);

		// Find all actionable elements
		List<ElementHandle> allInteractiveElements = page.querySelectorAll(allSelectors);

		// Compile the filter pattern into a regex
		Pattern pattern = Pattern.compile(filterPattern);

		// Filter elements that do not match the filterPattern
		List<ElementHandle> filteredInteractiveElements = allInteractiveElements.stream()
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
				.collect(Collectors.toList());

		return filteredInteractiveElements; // Return the filtered list of elements
	}

	private List<ElementHandle> controlWebPage(Page page, List<ElementHandle> interactiveElements){
		// If the DOM state does not contain interactive elements
		// Or if TESTAR is not in the webSUT URL
		if(interactiveElements.isEmpty() || !page.url().contains(webSUT)) {
			// Navigate to the original webSUT URL
			page.navigate(webSUT);
			// Wait for the page to finish loading 
			page.waitForTimeout(1000);
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
			page.waitForLoadState(LoadState.NETWORKIDLE);
			page.waitForLoadState(LoadState.LOAD);
			// And derive again the interactive elements
			interactiveElements = deriveInteractiveElements(page);
		}
		return interactiveElements;
	}

	private ElementHandle selectRandomElement(List<ElementHandle> interactiveElements) {
		// Select a random element from the interactive list
		return interactiveElements.get(new Random().nextInt(interactiveElements.size()));
	}

	private boolean executeAction(Page page, ElementHandle selectedElement) {
		try {
			// Click the selected element
			selectedElement.click();

			// After the action, wait for the page to finish loading 
			page.waitForLoadState(LoadState.DOMCONTENTLOADED);
			page.waitForLoadState(LoadState.NETWORKIDLE);
			page.waitForLoadState(LoadState.LOAD);
		} catch(Exception e) {
			logger.log(Level.ERROR, e.getMessage());
			return false;
		}

		return true;
	}

}
