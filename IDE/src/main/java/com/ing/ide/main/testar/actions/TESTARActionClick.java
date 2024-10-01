package com.ing.ide.main.testar.actions;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

public class TESTARActionClick implements TESTARAction {

	private ElementHandle elementHandle;

	public TESTARActionClick(ElementHandle elementHandle) {
		this.elementHandle = elementHandle;
	}

	@Override
	public void run(Page page) throws Exception {
		// Click the web element
		elementHandle.click();

		// After the action, wait for the page to finish loading
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		page.waitForLoadState(LoadState.NETWORKIDLE);
		page.waitForLoadState(LoadState.LOAD);
	}

	@Override
	public String toString() {
		// Retrieve the id, name, text content, and href attributes of the element
		String id = elementHandle.getAttribute("id") != null ? elementHandle.getAttribute("id") : "";
		String name = elementHandle.getAttribute("name") != null ? elementHandle.getAttribute("name") : "";
		String textContent = elementHandle.textContent() != null ? elementHandle.textContent() : "";
		String href = elementHandle.getAttribute("href") != null ? elementHandle.getAttribute("href") : "";

		// Build and return the formatted string
		return String.format("TESTARActionClick: [id='%s', name='%s', text='%s', href='%s']",
				id, name, textContent, href);
	}
}
