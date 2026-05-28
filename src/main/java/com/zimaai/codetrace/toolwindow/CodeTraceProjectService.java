package com.zimaai.codetrace.toolwindow;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.zimaai.codetrace.navigation.CodeNavigationService;
import com.zimaai.codetrace.storage.TraceJsonMapper;
import com.zimaai.codetrace.storage.TraceStorageService;

@Service(Service.Level.PROJECT)
public final class CodeTraceProjectService {
    private final CodeTraceController controller;
    private Runnable refreshCallback;

    public CodeTraceProjectService(Project project) {
        TraceStorageService storage = new TraceStorageService(project, new TraceJsonMapper());
        CodeNavigationService navigation = new CodeNavigationService(project);
        this.controller = new CodeTraceController(storage, navigation::navigate);
    }

    public CodeTraceController controller() {
        return controller;
    }

    public void registerRefreshCallback(Runnable refreshCallback) {
        this.refreshCallback = refreshCallback;
    }

    public void refreshToolWindowIfPresent() {
        if (refreshCallback != null) {
            refreshCallback.run();
        }
    }
}
