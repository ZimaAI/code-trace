package com.zimaai.codetrace.navigation;

import java.nio.file.Path;
import java.util.Objects;

public final class TraceNodePathResolver {
    private static final String EXTERNAL_FILE_MESSAGE =
            "Only files under the project root can be added to code-trace.";

    private TraceNodePathResolver() {}

    public static String toStoredPath(Path projectRoot, Path filePath) {
        Path normalizedProjectRoot = Objects.requireNonNull(projectRoot, "projectRoot").toAbsolutePath().normalize();
        Path normalizedFilePath = Objects.requireNonNull(filePath, "filePath").toAbsolutePath().normalize();
        if (!normalizedFilePath.startsWith(normalizedProjectRoot)) {
            throw new IllegalArgumentException(EXTERNAL_FILE_MESSAGE);
        }
        return normalizedProjectRoot.relativize(normalizedFilePath).toString().replace('\\', '/');
    }

    public static String resolveForNavigation(Path projectRoot, String storedFilePath) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Path candidate = Path.of(Objects.requireNonNull(storedFilePath, "storedFilePath"));
        if (candidate.isAbsolute()) {
            return candidate.normalize().toString().replace('\\', '/');
        }
        return projectRoot.toAbsolutePath()
                .normalize()
                .resolve(candidate)
                .normalize()
                .toString()
                .replace('\\', '/');
    }

    public static String externalFileMessage() {
        return EXTERNAL_FILE_MESSAGE;
    }
}
