package com.zimaai.codetrace.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.zimaai.codetrace.toolwindow.CodeTraceProjectService;
import org.jetbrains.annotations.NotNull;

public final class AddToCodeTraceAction extends AnAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        boolean enabled = project != null
                && event.getData(CommonDataKeys.EDITOR) != null
                && project.getService(CodeTraceProjectService.class).controller().state().currentFileName() != null;
        event.getPresentation().setEnabledAndVisible(enabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        var editor = event.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) {
            return;
        }
        var psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (psiFile == null) {
            return;
        }
        CodeTraceProjectService service = project.getService(CodeTraceProjectService.class);
        AddToCodeTraceHandler handler = new AddToCodeTraceHandler(
                service.controller(),
                new PsiTraceNodeCaptureService(),
                new SwingTraceUserPrompts(),
                service::refreshToolWindowIfPresent);
        handler.handle(project, editor, psiFile);
    }
}
