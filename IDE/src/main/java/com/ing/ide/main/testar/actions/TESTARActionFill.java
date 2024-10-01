package com.ing.ide.main.testar.actions;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

public class TESTARActionFill implements TESTARAction {

	private ElementHandle elementHandle;
	private String typedText;

	public TESTARActionFill(ElementHandle elementHandle, String typedText) {
		this.elementHandle = elementHandle;
		this.typedText = typedText;
	}

	@Override
	public void run(Page page) throws Exception {
		// Fill the web element with the desired text
		elementHandle.fill(typedText);

		// After the action, wait for the page to finish loading
		page.waitForLoadState(LoadState.DOMCONTENTLOADED);
		page.waitForLoadState(LoadState.NETWORKIDLE);
		page.waitForLoadState(LoadState.LOAD);
	}

	@Override
	public String toString() {
		// Retrieve the id, name, and text content attributes of the element
		String id = elementHandle.getAttribute("id") != null ? elementHandle.getAttribute("id") : "";
		String name = elementHandle.getAttribute("name") != null ? elementHandle.getAttribute("name") : "";
		String textContent = elementHandle.textContent() != null ? elementHandle.textContent() : "";

		// Build and return the formatted string
		return String.format("TESTARActionFill text '%s' into element: [id='%s', name='%s', text='%s']",
				typedText, id, name, textContent);
	}
}
