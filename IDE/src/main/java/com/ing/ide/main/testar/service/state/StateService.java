package com.ing.ide.main.testar.service.state;

import com.ing.ide.main.testar.mcp.Feedback;
import com.ing.ide.main.testar.service.SessionContext;

public interface StateService {

    Feedback getStateInteractiveWidgets(SessionContext context);

    Feedback getStateImage(SessionContext context);

    Feedback getStateVisualText(SessionContext context);
}
