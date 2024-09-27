
package com.ing.ide.main.mainui;

import com.ing.engine.support.methodInf.MethodInfoManager;
import com.ing.ide.main.bdd.BddParser;
import com.ing.ide.main.explorer.ExplorerBar;
import com.ing.ide.main.help.Help;
import com.ing.ide.main.mainui.components.testdesign.testdata.ImportTestData;
import com.ing.ide.main.settings.INGeniousSettings;
import com.ing.ide.main.settings.DriverSettings;
import com.ing.ide.main.settings.TMSettings;
import com.ing.ide.main.testar.TESTARPanel;
import com.ing.ide.main.googlerecordingjson.JsonParser;
import com.ing.ide.main.playwrightrecording.PlaywrightRecordingParser;
import com.ing.ide.main.ui.AboutUI;
import com.ing.ide.main.ui.InjectScript;
import com.ing.ide.main.ui.NewProject;
import com.ing.ide.main.ui.Options;
import com.ing.ide.main.utils.CMProjectCreator;
import com.ing.ide.main.utils.Utils;
import com.ing.ide.util.logging.UILogger;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AppActionListener implements ActionListener {

    private final AppMainFrame sMainFrame;

    private final NewProject nProject;

    private final INGeniousSettings cogITSSettings;

    private final DriverSettings driverSettings;

    private final TMSettings tmSettings;

    private final Options options;

    //private final SchedulerUI scheduler;

    private final BddParser bddParser;
    
    private final JsonParser jsonParser;

    private final InjectScript injectScript;

    private final ImportTestData importTestData;
    
    public final PlaywrightRecordingParser playwrightRecordingParser;

    private final TESTARPanel testarPanel;

    public AppActionListener(AppMainFrame sMainFrame) throws IOException {
        this.sMainFrame = sMainFrame;
        nProject = new NewProject(sMainFrame);
        cogITSSettings = new INGeniousSettings(sMainFrame);
        driverSettings = new DriverSettings(sMainFrame);
        tmSettings = new TMSettings(sMainFrame);
        options = new Options();
       // scheduler = new SchedulerUI();
        bddParser = new BddParser(sMainFrame);
        jsonParser = new JsonParser(sMainFrame);
        injectScript = new InjectScript();
        importTestData = new ImportTestData(sMainFrame);
        playwrightRecordingParser=new PlaywrightRecordingParser(sMainFrame);
        testarPanel = new TESTARPanel(sMainFrame);
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        switch (ae.getActionCommand()) {
            case "New Project":
                newProject();
                break;
            case "Open Project":
                openProject();
                break;
            case "Save Project":
                sMainFrame.save();
                break;
            case "Restart":
                sMainFrame.restart();
                break;
            case "Quit":
                sMainFrame.quit();
                break;
//            case "Object Spy":
//                sMainFrame.getSpyHealReco().showObjectSpy();
//                break;
//            case "Object Heal":
//                sMainFrame.getSpyHealReco().showObjectHeal();
//                break;
            case "Recorder":
            {
                try {
                    sMainFrame.getTestDesign().getTestCaseComp().record();
                } catch (IOException ex) {
                    Logger.getLogger(AppActionListener.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
                break;

//            case "Mobile Spy":
//                sMainFrame.getSpyHealReco().showMobileSpy();
//               break;
            case "Exploratory":
            {
                try {
                    ExplorerBar.showExplorerBar(sMainFrame);
                } catch (IOException ex) {
                    Logger.getLogger(AppActionListener.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
                break;

            case "Multiple Environment":
                sMainFrame.getTestDesign().getTestDatacomp().switchEnvView();
                break;
            case "Import TestData":
                importTestData.importTestData();
                break;
            case "Inject Script":
            {
                try {
                    injectScript.load();
                } catch (IOException ex) {
                    Logger.getLogger(AppActionListener.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
                break;

            case "Run Settings":
                openSettings();
                break;
            case "Browser Configuration":
                driverSettings.open();
                break;
            case "AzureDevOps TestPlan Configuration":
                tmSettings.open();
                break;
            case "Options":
                options.showOptions();
                break;
            case "Import Feature File":
            {
                try {
                    bddParser.parse(Utils.openDialog("Feature File", "feature"));
                } catch (IOException ex) {
                    Logger.getLogger(AppActionListener.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
                break;

            case "Open Feature Editor":
                bddParser.openEditor();
                break;
            case "Har Compare":
                sMainFrame.getDashBoardManager().openHarComapareInBrowser();
                break;
            case "Help":
                Help.openHelp();
                break;
            case "About":
                AboutUI.showUI();
                break;
            case "Show Log":
                UILogger.get().openLog();
                break;
            case "Test Design":
                sMainFrame.showTestDesign();
                break;
            case "Test Execution":
                sMainFrame.showTestExecution();
                break;
            case "Dashboard":
                sMainFrame.showDashBoard();
                break;
            case "Refresh":
                doRefresh();
                break;
            case "AdjustUI":
                sMainFrame.adjustUI();
                break;
            case "Create CM Project":
                CMProjectCreator.createCMProject();
                break;
            case "Import JSON":
                {
                    try {  
                        String ProjectLocation=sMainFrame.getProject().getLocation();
                        sMainFrame.loadProject(ProjectLocation);
                        jsonParser.parse(Utils.openDialog("JSON File", "json"));
                        sMainFrame.loadProject(ProjectLocation);                                     
                    } catch (IOException ex) {
                        Logger.getLogger(AppActionListener.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } 
                 break; 
            case "Import Playwright Recording":    
                {
                    try {  
                        String ProjectLocation=sMainFrame.getProject().getLocation();
                        sMainFrame.loadProject(ProjectLocation);
                        playwrightRecordingParser.playwrightParser(Utils.openDialog("Playwright Recording File", "txt"));
                        sMainFrame.loadProject(ProjectLocation);                                     
                    } catch (Exception ex) {
                        Logger.getLogger(AppActionListener.class.getName()).log(Level.SEVERE, null, ex);
                    }
                } 
                 break; 

            case "TESTAR Scriptless":
            	testarPanel.openEditor();
            	break;

            default:
                System.out.println(ae.getActionCommand());
                sMainFrame.getLoader().showIDontCare();

        }
    }

    private void newProject() {
        nProject.createNew();
        if (nProject.isCreated()) {
            sMainFrame.afterProjectChange();
        }
    }

    private void openProject() {
        File file = Utils.openINGeniousProject();
        if (file != null) {
            sMainFrame.loadProject(file.getAbsolutePath());
        }
    }

    private void openSettings() {
        if (sMainFrame.isTestExecution()) {
            com.ing.datalib.component.TestSet obj = sMainFrame.getTestExecution().getTestSetComp()
                    .getCurrentTestSet();
            if (obj != null) {
                cogITSSettings.loadSettings(obj.getExecSettings());
            }
        } else if (sMainFrame.isTestDesign()) {
            cogITSSettings.loadSettings();
        }
    }

    public AppMainFrame getMainFrame() {
        return sMainFrame;
    }

    public void afterProjectChange() {
        cogITSSettings.afterProjectChange();
        driverSettings.load();
        tmSettings.load();
    }

    private void doRefresh() {
        MethodInfoManager.load();
    }

}
