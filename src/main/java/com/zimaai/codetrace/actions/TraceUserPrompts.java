package com.zimaai.codetrace.actions;

import com.intellij.openapi.project.Project;

public interface TraceUserPrompts {
    void showSelectTraceMessage(Project project);

    boolean confirmDetectedLink(Project project, String sourceDisplayName, String targetDisplayName);

    void showLinkError(Project project, String message);

    void showCaptureError(Project project, String message);
}
