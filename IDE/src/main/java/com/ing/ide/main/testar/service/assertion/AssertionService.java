package com.ing.ide.main.testar.service.assertion;

import com.ing.ide.main.testar.mcp.Feedback;
import com.ing.ide.main.testar.service.SessionContext;

public interface AssertionService {

    Feedback addStepAssert(SessionContext context, String bddStep, String assertText);
}
