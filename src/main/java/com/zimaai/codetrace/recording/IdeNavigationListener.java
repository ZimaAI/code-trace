package com.zimaai.codetrace.recording;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionResult;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class IdeNavigationListener implements AnActionListener {
    private static final Set<String> TRACKED_ACTIONS = Set.of(
            "GotoDeclaration",
            "GotoImplementation",
            "GotoTypeDeclaration",
            "GotoSuperMethod",
            "$Back",
            "$Forward");

    private final Project project;
    private final Consumer<TraceableNavigationTarget> sink;

    public IdeNavigationListener(Project project, Consumer<TraceableNavigationTarget> sink) {
        this.project = Objects.requireNonNull(project, "project");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @Override
    public void afterActionPerformed(AnAction action, AnActionEvent event, AnActionResult result) {
        String actionId = ActionManager.getInstance().getId(action);
        if (actionId == null || !TRACKED_ACTIONS.contains(actionId)) {
            return;
        }
        Editor editor = event.getData(CommonDataKeys.EDITOR);
        if (editor == null) {
            return;
        }
        PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (psiFile == null) {
            return;
        }
        VirtualFile file = psiFile.getVirtualFile();
        if (file == null) {
            return;
        }
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = psiFile.findElementAt(offset);
        if (element == null) {
            return;
        }

        PsiElement methodLike = findCallableOwner(element);
        if (methodLike == null) {
            return;
        }

        int line = editor.getCaretModel().getLogicalPosition().line + 1;
        String displayName = shortText(methodLike.getText(), 120);
        String qualifiedName = methodLike.toString();
        String signature = shortText(methodLike.getText(), 240);
        sink.accept(new TraceableNavigationTarget(
                "nav-" + UUID.randomUUID(),
                displayName,
                qualifiedName,
                signature,
                file.getPath(),
                line,
                methodLike.getLanguage().getID(),
                qualifiedName));
    }

    private static PsiElement findCallableOwner(PsiElement element) {
        // Best-effort language-agnostic parent search.
        PsiElement candidate = element;
        while (candidate != null) {
            String className = candidate.getClass().getSimpleName().toLowerCase();
            if (className.contains("method")
                    || className.contains("function")
                    || className.contains("callable")
                    || className.contains("lambda")) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        return PsiTreeUtil.getParentOfType(element, PsiElement.class, false);
    }

    private static String shortText(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }
}
