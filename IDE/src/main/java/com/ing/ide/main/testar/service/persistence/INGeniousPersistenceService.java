package com.ing.ide.main.testar.service.persistence;

import com.ing.ide.main.testar.TESTARDataWriter;
import com.ing.ide.main.testar.playwright.system.PlaywrightState;
import com.ing.ide.main.testar.playwright.system.PlaywrightWidget;
import com.ing.ide.main.testar.trace.AgentTraceContext;
import com.ing.ide.main.testar.trace.AgentTraceSupport;
import com.ing.ide.main.testar.trace.ArtifactPersistenceResult;
import com.microsoft.playwright.Page;
import org.testar.monkey.alayer.Action;

public class INGeniousPersistenceService implements PersistenceService {
    private final TESTARDataWriter dataWriter;
    private final AgentTraceContext traceContext;

    public INGeniousPersistenceService(TESTARDataWriter dataWriter) {
        this(dataWriter, null);
    }

    public INGeniousPersistenceService(
        TESTARDataWriter dataWriter,
        AgentTraceContext traceContext
    ) {
        this.dataWriter = dataWriter;
        this.traceContext = traceContext;
    }

    @Override
    public ArtifactPersistenceResult recordLoadWebUrl(String bddStep, String url) {
        ArtifactPersistenceResult result = new ArtifactPersistenceResult();
        result.add(
            dataWriter.addAbstractTestStep(
                bddStep,
                "Browser",
                "Open the Url [<Data>] in the Browser",
                "Open",
                "@".concat(url),
                ""
            )
        );
        AgentTraceSupport.appendArtifacts(traceContext, bddStep, "Open", result);
        return result;
    }

    @Override
    public ArtifactPersistenceResult recordNavigateBack() {
        ArtifactPersistenceResult result = new ArtifactPersistenceResult();
        result.add(
            dataWriter.addConcreteTestStep(
                "Browser",
                "Navigate to the previous page in history",
                "GoBack",
                "",
                ""
            )
        );
        AgentTraceSupport.appendArtifacts(traceContext, "", "GoBack", result);
        return result;
    }

    @Override
    public void persistWidgetObject(PlaywrightWidget widget, Page page) {
        ArtifactPersistenceResult result = dataWriter.addObservedWidgetObject(widget, page);
        AgentTraceSupport.appendArtifacts(traceContext, "", "state observation", result);
    }

    @Override
    public ArtifactPersistenceResult recordActionStep(
        String bddStep,
        PlaywrightState state,
        Action action,
        Page page
    ) {
        ArtifactPersistenceResult result = dataWriter.addActionTestStep(
            bddStep,
            state,
            action,
            page
        );
        AgentTraceSupport.appendArtifacts(traceContext, bddStep, "action", result);
        return result;
    }

    @Override
    public ArtifactPersistenceResult recordAssertStep(
        String bddStep,
        PlaywrightState state,
        String assertText
    ) {
        ArtifactPersistenceResult result = dataWriter.addAssertTestStep(bddStep, state, assertText);
        AgentTraceSupport.appendArtifacts(traceContext, bddStep, "assert text", result);
        return result;
    }

    @Override
    public ArtifactPersistenceResult recordTitleAssertStep(String bddStep, String titlePattern) {
        ArtifactPersistenceResult result = dataWriter.addTitleAssertTestStep(bddStep, titlePattern);
        AgentTraceSupport.appendArtifacts(traceContext, bddStep, "assert title", result);
        return result;
    }

    @Override
    public void saveExecutionSteps() {
        dataWriter.saveExecutionSteps();
    }
}
