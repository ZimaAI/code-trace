package com.zimaai.codetrace.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.zimaai.codetrace.model.TraceNode;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import com.intellij.testFramework.LightVirtualFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeNavigationServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesRelativePathAgainstProjectRootAndOpensZeroBasedLine() {
        Path projectRoot = tempDir.resolve("project");
        Path expectedFile = projectRoot.resolve(Path.of("src", "main", "java", "App.java"));
        AtomicReference<String> lookedUpPath = new AtomicReference<>();
        AtomicReference<VirtualFile> openedFile = new AtomicReference<>();
        AtomicInteger openedLine = new AtomicInteger(-1);

        CodeNavigationService service = new CodeNavigationService(
                projectWithBasePath(projectRoot),
                path -> {
                    lookedUpPath.set(path);
                    return expectedFile.toString().replace('\\', '/').equals(path)
                            ? new StubVirtualFile(expectedFile)
                            : null;
                },
                captureNavigation(openedFile, openedLine));

        boolean navigated = service.navigate(node("src/main/java/App.java", 7));

        assertTrue(navigated);
        assertEquals(expectedFile.toString().replace('\\', '/'), lookedUpPath.get());
        assertEquals(6, openedLine.get());
        assertEquals(expectedFile.toString().replace('\\', '/'), openedFile.get().getPath());
    }

    @Test
    void passesAbsolutePathDirectlyToFileFinder() {
        Path absoluteFile = tempDir.resolve(Path.of("external", "External.java"));
        AtomicReference<String> lookedUpPath = new AtomicReference<>();
        AtomicInteger navigationCalls = new AtomicInteger();

        CodeNavigationService service = new CodeNavigationService(
                projectWithBasePath(tempDir.resolve("project")),
                path -> {
                    lookedUpPath.set(path);
                    return null;
                },
                (file, line) -> navigationCalls.incrementAndGet());

        boolean navigated = service.navigate(node(absoluteFile.toString().replace('\\', '/'), 3));

        assertFalse(navigated);
        assertEquals(absoluteFile.toString().replace('\\', '/'), lookedUpPath.get());
        assertEquals(0, navigationCalls.get());
    }

    @Test
    void navigatesAbsolutePathWhenProjectBasePathIsNull() {
        Path absoluteFile = tempDir.resolve(Path.of("external", "Legacy.java"));
        AtomicReference<String> lookedUpPath = new AtomicReference<>();

        CodeNavigationService service = new CodeNavigationService(
                projectWithBasePath((String) null),
                path -> {
                    lookedUpPath.set(path);
                    return absoluteFile.toString().replace('\\', '/').equals(path)
                            ? new StubVirtualFile(absoluteFile)
                            : null;
                },
                (file, line) -> {});

        boolean navigated = service.navigate(node(absoluteFile.toString().replace('\\', '/'), 4));

        assertTrue(navigated);
        assertEquals(absoluteFile.toString().replace('\\', '/'), lookedUpPath.get());
    }

    @Test
    void treatsWindowsStyleAbsolutePathStringAsAbsolute() {
        String windowsAbsolutePath = "C:/repo/A.java";
        AtomicReference<String> lookedUpPath = new AtomicReference<>();

        CodeNavigationService service = new CodeNavigationService(
                projectWithBasePath(tempDir.resolve("project")),
                path -> {
                    lookedUpPath.set(path);
                    return windowsAbsolutePath.equals(path) ? new StubVirtualFile(windowsAbsolutePath) : null;
                },
                (file, line) -> {});

        boolean navigated = service.navigate(node(windowsAbsolutePath, 2));

        assertTrue(navigated);
        assertEquals(windowsAbsolutePath, lookedUpPath.get());
    }

    @Test
    void treatsUnixStyleAbsolutePathStringAsAbsolute() {
        String unixAbsolutePath = "/repo/A.java";
        AtomicReference<String> lookedUpPath = new AtomicReference<>();

        CodeNavigationService service = new CodeNavigationService(
                projectWithBasePath(tempDir.resolve("project")),
                path -> {
                    lookedUpPath.set(path);
                    return unixAbsolutePath.equals(path) ? new StubVirtualFile(unixAbsolutePath) : null;
                },
                (file, line) -> {});

        boolean navigated = service.navigate(node(unixAbsolutePath, 2));

        assertTrue(navigated);
        assertEquals(unixAbsolutePath, lookedUpPath.get());
    }

    @Test
    void returnsFalseWhenFileCannotBeFound() {
        AtomicInteger navigationCalls = new AtomicInteger();

        CodeNavigationService service = new CodeNavigationService(
                projectWithBasePath(tempDir.resolve("project")),
                path -> null,
                (file, line) -> navigationCalls.incrementAndGet());

        boolean navigated = service.navigate(node("src/main/java/Missing.java", 1));

        assertFalse(navigated);
        assertEquals(0, navigationCalls.get());
    }

    @Test
    void publicConstructorReturnsFalseForNullNode() {
        CodeNavigationService service = new CodeNavigationService(projectWithBasePath(tempDir.resolve("project")));

        boolean navigated = service.navigate(null);

        assertFalse(navigated);
    }

    private static TraceNode node(String filePath, int line) {
        return new TraceNode("id", "display", "qualified", "signature", filePath, line, "java", "", "");
    }

    private static Project projectWithBasePath(Path projectRoot) {
        return projectWithBasePath(projectRoot == null ? null : projectRoot.toString());
    }

    private static Project projectWithBasePath(String basePath) {
        return (Project) Proxy.newProxyInstance(
                Project.class.getClassLoader(),
                new Class<?>[] {Project.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBasePath" -> basePath;
                    case "toString" -> "Project(" + basePath + ")";
                    default -> unsupported(method);
                });
    }

    private static BiConsumer<VirtualFile, Integer> captureNavigation(
            AtomicReference<VirtualFile> openedFile, AtomicInteger openedLine) {
        return (file, line) -> {
            openedFile.set(file);
            openedLine.set(line);
        };
    }

    private static Object unsupported(java.lang.reflect.Method method) {
        throw new UnsupportedOperationException("Unexpected call: " + method.getName());
    }

    private static final class StubVirtualFile extends LightVirtualFile {
        private final String path;

        private StubVirtualFile(Path path) {
            super(path.getFileName().toString(), "");
            this.path = path.toString().replace('\\', '/');
        }

        private StubVirtualFile(String path) {
            super(Path.of(path.replace('\\', '/')).getFileName().toString(), "");
            this.path = path.replace('\\', '/');
        }

        @Override
        public String getPath() {
            return path;
        }
    }
}
