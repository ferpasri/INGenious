package com.ing.ide.main.testar.service.session;

import com.ing.ide.main.testar.mcp.Feedback;
import com.ing.ide.main.testar.service.SessionContext;

public interface SessionService {

    Feedback loadWebURL(SessionContext context, String bddStep, String url);

    Feedback getCurrentURL(SessionContext context);

    Feedback navigateBack(SessionContext context);

    void stop(SessionContext context);
}
