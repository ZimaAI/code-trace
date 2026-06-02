package com.zimaai.codetrace.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.toolwindow.CodeTraceController;
import java.util.Optional;

public final class AddToCodeTraceHandler {
    private final CodeTraceController controller;
    private final TraceNodeCaptureService captureService;
    private final TraceUserPrompts prompts;
    private final Runnable refreshUi;

    public AddToCodeTraceHandler(
            CodeTraceController controller,
            TraceNodeCaptureService captureService,
            TraceUserPrompts prompts,
            Runnable refreshUi) {
        this.controller = controller;
        this.captureService = captureService;
        this.prompts = prompts;
        this.refreshUi = refreshUi;
    }

    public void handle(Project project, Editor editor, PsiFile psiFile) {
        if (controller.state().currentFileName() == null) {
            prompts.showSelectTraceMessage(project);
            return;
        }
        TraceNode source;
        try {
            source = captureService.captureCurrentLine(project, editor, psiFile);
        } catch (IllegalArgumentException exception) {
            prompts.showCaptureError(project, exception.getMessage());
            return;
        }
        int sourceIndex = controller.addOrReuseNodeAfterFocusedNode(source);
        String sourceId = controller.state().currentDocument().nodes().get(sourceIndex).id();
        controller.preferSelectedNode(sourceId);
        Optional<TraceNode> detectedTarget = captureService.detectTarget(project, editor, psiFile);
        if (detectedTarget.isPresent()
                && prompts.confirmDetectedLink(project, source.displayName(), detectedTarget.get().displayName())) {
            int targetIndex = controller.addOrReuseNode(detectedTarget.get());
            String targetId = controller.state().currentDocument().nodes().get(targetIndex).id();
            try {
                controller.setPendingLinkSource(sourceId);
                controller.linkPendingSourceTo(targetId, TraceLinkKind.DETECTED);
            } catch (IllegalArgumentException exception) {
                prompts.showLinkError(project, exception.getMessage());
            }
        }
        refreshUi.run();
    }
}
