package com.zimaai.codetrace.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TraceNodePathResolverTest {
    @TempDir
    Path tempDir;

    @Test
    void storesProjectFilesAsSlashSeparatedRelativePaths() {
        Path projectRoot = tempDir.resolve("project");
        Path filePath = projectRoot.resolve(Path.of("src", "main", "java", "App.java"));

        String storedPath = TraceNodePathResolver.toStoredPath(projectRoot, filePath);

        assertEquals("src/main/java/App.java", storedPath);
    }

    @Test
    void rejectsFilesOutsideProjectRoot() {
        Path projectRoot = tempDir.resolve("project");
        Path externalFile = tempDir.resolve(Path.of("other", "Outside.java"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TraceNodePathResolver.toStoredPath(projectRoot, externalFile));

        assertEquals(
                "Only files under the project root can be added to code-trace.",
                exception.getMessage());
    }

    @Test
    void resolvesRelativeAndAbsolutePathsForNavigation() {
        Path projectRoot = tempDir.resolve("project");
        Path relativeFile = projectRoot.resolve(Path.of("src", "main", "java", "App.java"));
        String absoluteFile = tempDir.resolve(Path.of("external", "External.java"))
                .toString()
                .replace('\\', '/');

        assertEquals(
                relativeFile.toString().replace('\\', '/'),
                TraceNodePathResolver.resolveForNavigation(projectRoot, "src/main/java/App.java"));
        assertEquals(
                absoluteFile,
                TraceNodePathResolver.resolveForNavigation(projectRoot, absoluteFile));
    }
}
