package com.zimaai.codetrace.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.zimaai.codetrace.model.TraceNode;
import java.util.Optional;

public interface TraceNodeCaptureService {
    TraceNode captureCurrentLine(Project project, Editor editor, PsiFile psiFile);

    Optional<TraceNode> detectTarget(Project project, Editor editor, PsiFile psiFile);
}
