package com.zimaai.codetrace.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.ui.content.ContentFactory;
import com.zimaai.codetrace.navigation.CodeNavigationService;
import com.zimaai.codetrace.recording.IdeNavigationListener;
import com.zimaai.codetrace.recording.TraceRecordingService;
import com.zimaai.codetrace.storage.TraceJsonMapper;
import com.zimaai.codetrace.storage.TraceStorageService;
import java.time.Clock;
import org.jetbrains.annotations.NotNull;

public final class CodeTraceToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        TraceStorageService storage = new TraceStorageService(project, new TraceJsonMapper());
        TraceRecordingService recording = new TraceRecordingService(Clock.systemUTC());
        CodeNavigationService navigation = new CodeNavigationService(project);
        CodeTraceController controller = new CodeTraceController(
                storage,
                decision -> true,
                recording,
                navigation::navigate);
        IdeNavigationListener listener = new IdeNavigationListener(project, controller::recordNavigation);
        @SuppressWarnings("removal")
        ActionManager actionManager = ActionManager.getInstance();
        actionManager.addAnActionListener(listener);
        CodeTracePanel panel = new CodeTracePanel(project, controller);
        panel.reloadFromDisk();
        var content = ContentFactory.getInstance().createContent(panel.getComponent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
