package com.zimaai.codetrace.navigation;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.zimaai.codetrace.model.TraceNode;
import java.nio.file.Path;
import java.util.Objects;

public final class CodeNavigationService {
    private final Project project;

    public CodeNavigationService(Project project) {
        this.project = Objects.requireNonNull(project, "project");
    }

    public boolean navigate(TraceNode node) {
        if (node == null || node.filePath() == null || node.filePath().isBlank()) {
            return false;
        }
        var path = Path.of(node.filePath()).toString().replace('\\', '/');
        var virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
        if (virtualFile == null) {
            return false;
        }
        int line = Math.max(node.line() - 1, 0);
        new OpenFileDescriptor(project, virtualFile, line, 0).navigate(true);
        return true;
    }
}
