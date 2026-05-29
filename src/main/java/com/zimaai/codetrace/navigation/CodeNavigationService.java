package com.zimaai.codetrace.navigation;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.zimaai.codetrace.model.TraceNode;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.Objects;

public final class CodeNavigationService {
    private final Project project;
    private final Function<String, VirtualFile> fileFinder;
    private final BiConsumer<VirtualFile, Integer> navigator;

    public CodeNavigationService(Project project) {
        this(
                project,
                path -> LocalFileSystem.getInstance().findFileByPath(path),
                (virtualFile, line) -> new OpenFileDescriptor(project, virtualFile, line, 0).navigate(true));
    }

    CodeNavigationService(
            Project project, Function<String, VirtualFile> fileFinder, BiConsumer<VirtualFile, Integer> navigator) {
        this.project = Objects.requireNonNull(project, "project");
        this.fileFinder = Objects.requireNonNull(fileFinder, "fileFinder");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
    }

    public boolean navigate(TraceNode node) {
        if (node == null || node.filePath() == null || node.filePath().isBlank()) {
            return false;
        }
        boolean absolutePath = isAbsoluteStoredPath(node.filePath());
        String basePath = project.getBasePath();
        if (!absolutePath && (basePath == null || basePath.isBlank())) {
            return false;
        }
        String path = absolutePath
                ? node.filePath().replace('\\', '/')
                : TraceNodePathResolver.resolveForNavigation(Path.of(basePath), node.filePath());
        var virtualFile = fileFinder.apply(path);
        if (virtualFile == null) {
            return false;
        }
        int line = Math.max(node.line() - 1, 0);
        navigator.accept(virtualFile, line);
        return true;
    }

    private static boolean isAbsoluteStoredPath(String path) {
        if (path.startsWith("/") || path.startsWith("\\") || path.startsWith("//") || path.startsWith("\\\\")) {
            return true;
        }
        return path.length() >= 3
                && Character.isLetter(path.charAt(0))
                && path.charAt(1) == ':'
                && (path.charAt(2) == '/' || path.charAt(2) == '\\');
    }
}
