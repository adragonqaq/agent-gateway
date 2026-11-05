package com.nageoffer.ai.gateway.runtime.tool;

import com.nageoffer.ai.gateway.tools.CatalogTool;
import com.nageoffer.ai.gateway.tools.ResolvedCatalog;
import io.agentscope.core.tool.Toolkit;
import org.springframework.stereotype.Component;

@Component
public class CatalogToolkitFactory {

    public Toolkit build(ResolvedCatalog catalog) {
        Toolkit toolkit = new Toolkit();
        for (CatalogTool tool : catalog.tools()) {
            if (!tool.available()) {
                continue;
            }
            toolkit.registerAgentTool(new CatalogStubTool(tool));
        }
        return toolkit;
    }
}
