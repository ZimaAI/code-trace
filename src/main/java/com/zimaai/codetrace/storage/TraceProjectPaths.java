package com.zimaai.codetrace.storage;

import com.intellij.openapi.project.Project;
import java.nio.file.Path;
import java.util.Objects;

public final class TraceProjectPaths {
    private final Path projectBasePath;

    public TraceProjectPaths(Project project) {
        Objects.requireNonNull(project, "project");
        String basePath = project.getBasePath();
        if (basePath == null || basePath.isBlank()) {
            throw new TraceStorageException("Project base path is unavailable", null);
        }
        this.projectBasePath = Path.of(basePath);
    }

    public TraceProjectPaths(Path projectBasePath) {
        this.projectBasePath = Objects.requireNonNull(projectBasePath, "projectBasePath");
    }

    public Path traceDirectory() {
        return projectBasePath.resolve("code-trace");
    }
}
