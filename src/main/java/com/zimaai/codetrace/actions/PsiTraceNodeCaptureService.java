package com.zimaai.codetrace.actions;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.navigation.TraceNodePathResolver;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

public final class PsiTraceNodeCaptureService implements TraceNodeCaptureService {
    @Override
    public TraceNode captureCurrentLine(Project project, Editor editor, PsiFile psiFile) {
        PsiElement callable = findCallableOwner(psiFile.findElementAt(editor.getCaretModel().getOffset()));
        int zeroBasedLine = editor.getCaretModel().getLogicalPosition().line;
        return buildNodeFromLine(projectRoot(project), psiFile, editor.getDocument(), zeroBasedLine, callable);
    }

    @Override
    public Optional<TraceNode> detectTarget(Project project, Editor editor, PsiFile psiFile) {
        Path root = projectRoot(project);
        PsiElement callable = findCallableOwner(psiFile.findElementAt(editor.getCaretModel().getOffset()));
        if (callable == null) {
            return Optional.empty();
        }
        PsiElement target = findCallableOwner(callable.getNavigationElement());
        if (target == null || target == callable) {
            return Optional.empty();
        }
        Document document = target.getContainingFile() == null ? null : target.getContainingFile().getViewProvider().getDocument();
        if (document == null) {
            return Optional.empty();
        }
        int zeroBasedLine = document.getLineNumber(target.getTextRange().getStartOffset());
        try {
            return Optional.of(buildNodeFromLine(root, target.getContainingFile(), document, zeroBasedLine, target));
        } catch (IllegalArgumentException exception) {
            if (TraceNodePathResolver.externalFileMessage().equals(exception.getMessage())) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    private static TraceNode buildNodeFromLine(
            Path projectRoot, PsiFile psiFile, Document document, int zeroBasedLine, PsiElement callable) {
        int safeLine = Math.max(0, Math.min(zeroBasedLine, Math.max(0, document.getLineCount() - 1)));
        int start = document.getLineStartOffset(safeLine);
        int end = document.getLineEndOffset(safeLine);
        String lineText = document.getText(new TextRange(start, end))
                .replace('\r', ' ')
                .replace('\n', ' ');
        VirtualFile file = psiFile.getVirtualFile();
        String qualifiedName = callable == null ? "" : callable.toString();
        String signature = callable == null ? "" : callable.getText().replace('\r', ' ').replace('\n', ' ');
        String navigationHint = qualifiedName;
        String storedPath = file == null ? "" : TraceNodePathResolver.toStoredPath(projectRoot, Path.of(file.getPath()));
        return new TraceNode(
                "node-" + UUID.randomUUID(),
                lineText,
                qualifiedName,
                signature,
                storedPath,
                safeLine + 1,
                psiFile.getLanguage().getID(),
                "",
                navigationHint);
    }

    private static Path projectRoot(Project project) {
        String basePath = project == null ? null : project.getBasePath();
        if (basePath == null || basePath.isBlank()) {
            throw new IllegalArgumentException("Project base path is unavailable");
        }
        return Path.of(basePath);
    }

    private static PsiElement findCallableOwner(PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            String className = current.getClass().getSimpleName().toLowerCase();
            if (className.contains("method")
                    || className.contains("function")
                    || className.contains("callable")
                    || className.contains("lambda")) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }
}
