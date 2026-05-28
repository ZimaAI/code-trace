package com.zimaai.codetrace.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public final class CodeTraceToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        CodeTraceProjectService service = project.getService(CodeTraceProjectService.class);
        CodeTracePanel panel = new CodeTracePanel(service.controller());
        service.registerRefreshCallback(panel::refreshFromExternalAction);
        panel.reloadFromDisk();
        var content = ContentFactory.getInstance().createContent(panel.getComponent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
