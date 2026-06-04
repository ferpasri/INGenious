package com.ing.ide.main.testar.service.action;

import com.ing.ide.main.testar.mcp.Feedback;
import com.ing.ide.main.testar.service.SessionContext;

public interface ActionService {

    Feedback executeClickAction(SessionContext context, String bddStep, String rawCssSelector);

    Feedback executeFillAction(SessionContext context, String bddStep, String rawCssSelector, String fillText);

    Feedback executeSelectAction(SessionContext context, String bddStep, String rawCssSelector, String optionValue);

    Feedback checkExecutedActions(SessionContext context);
}
