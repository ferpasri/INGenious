package com.ing.ide.main.testar.reporting;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ing.ide.main.testar.actions.TESTARAction;
import com.ing.ide.main.utils.Utils;

public class TESTARHtmlReport {
	private static final Logger logger = LogManager.getLogger();

	private String htmlFile;
	private ArrayList<String> content = new ArrayList<>();

	public TESTARHtmlReport() {
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
		String reportName = "TESTAR_report_" + timeStamp + ".html";
		try {
			// Create a folder to store TESTAR results
			String outputFolder = Utils.getAppRoot() + File.separator + "TESTAR_results";
			if(!new File(outputFolder).exists())
				new File(outputFolder).mkdir();

			htmlFile = outputFolder + File.separator + reportName;
		} catch (IOException e) {
			// Log the exception when obtaining the app root folder
			logger.log(Level.ERROR, e.getMessage());

			// Then use the user dir to create a folder to store TESTAR results
			String outputFolder = System.getProperty("user.dir") + File.separator + "TESTAR_results";
			if(!new File(outputFolder).exists())
				new File(outputFolder).mkdir();

			htmlFile = outputFolder + File.separator + reportName;

			logger.log(Level.ERROR, "TESTARHtmlReport created into: " + htmlFile);
		}
		startFileReport();
	}

	private void startFileReport() {
		content.add("<!DOCTYPE html>");
		content.add("<html lang=\"en\">");
		content.add("<head>");        
		content.add("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">");
		content.add("<header><h1>TESTAR HTML result</h1></header>");
		content.add("</head>");
		content.add("<body>");
		writeToFile();
	}

	public void addState(byte[] screenshot, String url) {
		content.add("<h3>State</h3>");
		content.add("<a href='" + url + "'>" + url + "</a>");

		// Convert byte[] screenshot into a Base64 encoded string
		String base64Image = Base64.getEncoder().encodeToString(screenshot);
		// Create an HTML image tag with the Base64-encoded image
		String imgTag = "<img src='data:image/png;base64," + base64Image + "' alt='Page Screenshot' />";
		content.add(imgTag);

		writeToFile();
	}

	public void addDerivedActions(List<TESTARAction> actions) {
		content.add("<h3>Derived actions</h3>");
		for(TESTARAction action : actions) {
			content.add("<p>" + action.toString() + "</p>");
		}
		writeToFile();
	}

	public void addSelectedAction(TESTARAction action) {
		content.add("<h3>Selected action</h3>");
		content.add("<p>" + action.toString() + "</p>");
		writeToFile();
	}

	private void writeToFile() {
		if (!content.isEmpty()) {
			try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(htmlFile, true), StandardCharsets.UTF_8))) {
				// Write each string in content to the file
				for (String str : content) {
					writer.println(str);
				}
				content.clear(); // Empty the queue
			} catch (IOException e) {
				logger.log(Level.ERROR, e.getMessage());
			}
		}
	}

}
