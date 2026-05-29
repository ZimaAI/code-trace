# Code Trace 相对节点文件路径实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 让新采集节点默认把 `filePath` 保存为相对项目根目录的相对路径，同时保持旧绝对路径节点仍可加载和导航，并在编辑器动作中拒绝项目外文件入库。

**架构：** 新增一个纯 Java 路径解析器，专门负责“项目内路径转存储值”和“存储值转可导航绝对路径”。采集链路在 `PsiTraceNodeCaptureService` 使用它生成相对路径并在项目外文件时抛出拒绝错误；导航链路在 `CodeNavigationService` 使用它兼容相对路径和历史绝对路径。`TraceJsonMapper` 不做任何路径转换，只通过回归测试锁定“绝对路径原样保留”的行为。

**技术栈：** Java 21、IntelliJ Platform SDK、Swing、Jackson、JUnit 5

---

## Planned File Structure

### Path resolution and navigation

- 创建：`src/main/java/com/zimaai/codetrace/navigation/TraceNodePathResolver.java`
  - 纯函数工具，负责项目内路径相对化、绝对/相对路径导航解析、分隔符标准化。
- 修改：`src/main/java/com/zimaai/codetrace/navigation/CodeNavigationService.java`
  - 使用 `TraceNodePathResolver` 解析 `node.filePath()`，并增加仅供测试使用的构造注入点。

### Capture flow

- 修改：`src/main/java/com/zimaai/codetrace/actions/PsiTraceNodeCaptureService.java`
  - 将 source 节点 `filePath` 转成相对路径；项目外 source 直接拒绝；项目外 detected target 视为无目标。
- 修改：`src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceHandler.java`
  - 捕获 source 被拒绝时显示用户提示并停止流程。
- 修改：`src/main/java/com/zimaai/codetrace/actions/TraceUserPrompts.java`
  - 增加 source capture 失败提示接口。
- 修改：`src/main/java/com/zimaai/codetrace/actions/SwingTraceUserPrompts.java`
  - 实现新的 capture 失败提示。

### Tests and smoke checklist

- 创建：`src/test/java/com/zimaai/codetrace/navigation/TraceNodePathResolverTest.java`
  - 锁定相对化、项目外拒绝、导航路径解析。
- 创建：`src/test/java/com/zimaai/codetrace/navigation/CodeNavigationServiceTest.java`
  - 锁定相对路径导航、绝对路径兼容、缺失文件失败。
- 修改：`src/test/java/com/zimaai/codetrace/actions/AddToCodeTraceHandlerTest.java`
  - 增加 source capture 被拒绝时不写入 trace 的行为测试。
- 修改：`src/test/java/com/zimaai/codetrace/storage/TraceJsonMapperTest.java`
  - 增加“绝对路径读写不被改写”的回归断言。
- 修改：`docs/superpowers/plans/2026-05-29-code-trace-manual-smoke-checklist.md`
  - 增加“新 JSON 存相对路径 / 旧 JSON 绝对路径仍可导航 / 项目外文件拒绝采集”的人工验证步骤。

## Task 1: 建立路径解析器并锁定路径格式回归

**文件：**
- 创建：`src/main/java/com/zimaai/codetrace/navigation/TraceNodePathResolver.java`
- 创建：`src/test/java/com/zimaai/codetrace/navigation/TraceNodePathResolverTest.java`
- 修改：`src/test/java/com/zimaai/codetrace/storage/TraceJsonMapperTest.java`

- [ ] **步骤 1：编写失败的路径解析器与 mapper 回归测试**

创建 `src/test/java/com/zimaai/codetrace/navigation/TraceNodePathResolverTest.java`：

```java
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
    void storesProjectInternalFilesAsSlashSeparatedRelativePaths() {
        Path projectRoot = tempDir.resolve("project").toAbsolutePath().normalize();
        Path filePath = projectRoot.resolve(Path.of("src", "AuthController.java"));

        String stored = TraceNodePathResolver.toStoredPath(projectRoot, filePath);

        assertEquals("src/AuthController.java", stored);
    }

    @Test
    void rejectsFilesOutsideProjectRoot() {
        Path projectRoot = tempDir.resolve("project").toAbsolutePath().normalize();
        Path externalFile = tempDir.resolve("external").resolve("Library.java").toAbsolutePath().normalize();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> TraceNodePathResolver.toStoredPath(projectRoot, externalFile));

        assertEquals("Only files under the project root can be added to code-trace.", exception.getMessage());
    }

    @Test
    void resolvesRelativeAndAbsolutePathsForNavigation() {
        Path projectRoot = tempDir.resolve("project").toAbsolutePath().normalize();
        Path absoluteFile = tempDir.resolve("external").resolve("Library.java").toAbsolutePath().normalize();

        String resolvedRelative = TraceNodePathResolver.resolveForNavigation(projectRoot, "src/AuthController.java");
        String resolvedAbsolute = TraceNodePathResolver.resolveForNavigation(projectRoot, absoluteFile.toString());

        assertEquals(
                projectRoot.resolve("src/AuthController.java").toString().replace('\\', '/'),
                resolvedRelative);
        assertEquals(absoluteFile.toString().replace('\\', '/'), resolvedAbsolute);
    }
}
```

在 `src/test/java/com/zimaai/codetrace/storage/TraceJsonMapperTest.java` 里追加：

```java
    @Test
    void preservesAbsoluteFilePathValuesWhenReadingSchemaTwo() throws Exception {
        String absolutePath = Path.of("build", "tmp", "legacy", "AuthController.java")
                .toAbsolutePath()
                .normalize()
                .toString()
                .replace('\\', '/');
        TraceDocument document = new TraceDocument(
                2,
                "trace-auth-login",
                "Auth Login",
                "trace note",
                Instant.parse("2026-05-29T09:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(new TraceNode(
                        "node-1",
                        "return authService.login(user);",
                        "AuthController#login",
                        "login(User user)",
                        absolutePath,
                        21,
                        "JAVA",
                        "source note",
                        "AuthController#login(User)")),
                List.of());

        TraceJsonMapper mapper = new TraceJsonMapper();
        String json = mapper.write(document);
        TraceDocument restored = mapper.read(json);

        assertEquals(absolutePath, restored.nodes().get(0).filePath());
    }
```

- [ ] **步骤 2：运行测试确认失败**

运行：

```powershell
.\gradlew.bat test --tests "com.zimaai.codetrace.navigation.TraceNodePathResolverTest" --tests "com.zimaai.codetrace.storage.TraceJsonMapperTest"
```

预期：FAIL，编译错误指出 `TraceNodePathResolver` 不存在。

- [ ] **步骤 3：编写最少实现代码**

创建 `src/main/java/com/zimaai/codetrace/navigation/TraceNodePathResolver.java`：

```java
package com.zimaai.codetrace.navigation;

import java.nio.file.Path;
import java.util.Objects;

public final class TraceNodePathResolver {
    private static final String EXTERNAL_FILE_MESSAGE = "Only files under the project root can be added to code-trace.";

    private TraceNodePathResolver() {
    }

    public static String toStoredPath(Path projectRoot, Path filePath) {
        Path normalizedProjectRoot = normalize(projectRoot);
        Path normalizedFilePath = normalize(filePath);
        if (!normalizedFilePath.startsWith(normalizedProjectRoot)) {
            throw new IllegalArgumentException(EXTERNAL_FILE_MESSAGE);
        }
        return normalizedProjectRoot.relativize(normalizedFilePath).toString().replace('\\', '/');
    }

    public static String resolveForNavigation(Path projectRoot, String storedFilePath) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Objects.requireNonNull(storedFilePath, "storedFilePath");
        Path rawPath = Path.of(storedFilePath);
        Path resolvedPath = rawPath.isAbsolute() ? rawPath.normalize() : normalize(projectRoot).resolve(rawPath).normalize();
        return resolvedPath.toString().replace('\\', '/');
    }

    public static String externalFileMessage() {
        return EXTERNAL_FILE_MESSAGE;
    }

    private static Path normalize(Path path) {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }
}
```

- [ ] **步骤 4：运行测试确认通过**

运行：

```powershell
.\gradlew.bat test --tests "com.zimaai.codetrace.navigation.TraceNodePathResolverTest" --tests "com.zimaai.codetrace.storage.TraceJsonMapperTest"
```

预期：PASS。

- [ ] **步骤 5：Commit**

```powershell
git add src/main/java/com/zimaai/codetrace/navigation/TraceNodePathResolver.java src/test/java/com/zimaai/codetrace/navigation/TraceNodePathResolverTest.java src/test/java/com/zimaai/codetrace/storage/TraceJsonMapperTest.java
git commit -m "feat: add trace node path resolver"
```

## Task 2: 让采集链路保存相对路径并拒绝项目外 source

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/actions/PsiTraceNodeCaptureService.java`
- 修改：`src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceHandler.java`
- 修改：`src/main/java/com/zimaai/codetrace/actions/TraceUserPrompts.java`
- 修改：`src/main/java/com/zimaai/codetrace/actions/SwingTraceUserPrompts.java`
- 修改：`src/test/java/com/zimaai/codetrace/actions/AddToCodeTraceHandlerTest.java`

- [ ] **步骤 1：编写失败的 handler 拒绝测试**

在 `src/test/java/com/zimaai/codetrace/actions/AddToCodeTraceHandlerTest.java` 里追加：

```java
    @Test
    void showsCaptureErrorAndKeepsTraceUntouchedWhenSourceFileIsOutsideProjectRoot() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.createNewFile("trace-2.json", "Trace 2");

        AtomicBoolean refreshed = new AtomicBoolean(false);
        RecordingPrompts prompts = new RecordingPrompts(true);
        AddToCodeTraceHandler handler = new AddToCodeTraceHandler(
                controller,
                new ThrowingCaptureService(TraceNodePathResolver.externalFileMessage()),
                prompts,
                () -> refreshed.set(true));

        handler.handle(null, null, null);

        TraceDocument document = controller.state().currentDocument();
        assertEquals(0, document.nodes().size());
        assertEquals(TraceNodePathResolver.externalFileMessage(), prompts.captureErrorMessage);
        assertTrue(!prompts.confirmCalled);
        assertTrue(!refreshed.get());
    }
```

把 `RecordingPrompts` 更新为：

```java
    private static final class RecordingPrompts implements TraceUserPrompts {
        private final boolean confirm;
        private boolean confirmCalled;
        private String captureErrorMessage;

        private RecordingPrompts(boolean confirm) {
            this.confirm = confirm;
        }

        @Override
        public void showSelectTraceMessage(com.intellij.openapi.project.Project project) {
        }

        @Override
        public void showCaptureError(com.intellij.openapi.project.Project project, String message) {
            captureErrorMessage = message;
        }

        @Override
        public boolean confirmDetectedLink(
                com.intellij.openapi.project.Project project,
                String sourceDisplayName,
                String targetDisplayName) {
            confirmCalled = true;
            return confirm;
        }

        @Override
        public void showLinkError(com.intellij.openapi.project.Project project, String message) {
        }
    }
```

追加一个测试替身：

```java
    private static final class ThrowingCaptureService implements TraceNodeCaptureService {
        private final String message;

        private ThrowingCaptureService(String message) {
            this.message = message;
        }

        @Override
        public TraceNode captureCurrentLine(
                com.intellij.openapi.project.Project project,
                com.intellij.openapi.editor.Editor editor,
                com.intellij.psi.PsiFile psiFile) {
            throw new IllegalArgumentException(message);
        }

        @Override
        public Optional<TraceNode> detectTarget(
                com.intellij.openapi.project.Project project,
                com.intellij.openapi.editor.Editor editor,
                com.intellij.psi.PsiFile psiFile) {
            return Optional.empty();
        }
    }
```

- [ ] **步骤 2：运行测试确认失败**

运行：

```powershell
.\gradlew.bat test --tests "com.zimaai.codetrace.actions.AddToCodeTraceHandlerTest"
```

预期：FAIL，编译错误指出 `TraceUserPrompts.showCaptureError(...)` 不存在，且 `AddToCodeTraceHandler` 没有处理 source capture 异常。

- [ ] **步骤 3：编写最少实现代码**

把 `src/main/java/com/zimaai/codetrace/actions/TraceUserPrompts.java` 改成：

```java
package com.zimaai.codetrace.actions;

import com.intellij.openapi.project.Project;

public interface TraceUserPrompts {
    void showSelectTraceMessage(Project project);

    void showCaptureError(Project project, String message);

    boolean confirmDetectedLink(Project project, String sourceDisplayName, String targetDisplayName);

    void showLinkError(Project project, String message);
}
```

把 `src/main/java/com/zimaai/codetrace/actions/SwingTraceUserPrompts.java` 改成：

```java
package com.zimaai.codetrace.actions;

import com.intellij.openapi.project.Project;
import javax.swing.JOptionPane;

public final class SwingTraceUserPrompts implements TraceUserPrompts {
    @Override
    public void showSelectTraceMessage(Project project) {
        JOptionPane.showMessageDialog(null, "Select a code-trace JSON in the Tool Window first.");
    }

    @Override
    public void showCaptureError(Project project, String message) {
        JOptionPane.showMessageDialog(null, message);
    }

    @Override
    public boolean confirmDetectedLink(Project project, String sourceDisplayName, String targetDisplayName) {
        int choice = JOptionPane.showConfirmDialog(
                null,
                "Also add target and create link?\nSource: " + sourceDisplayName + "\nTarget: " + targetDisplayName,
                "Detected target",
                JOptionPane.YES_NO_OPTION);
        return choice == JOptionPane.YES_OPTION;
    }

    @Override
    public void showLinkError(Project project, String message) {
        JOptionPane.showMessageDialog(null, message);
    }
}
```

把 `src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceHandler.java` 的 `handle(...)` 改成：

```java
    public void handle(Project project, Editor editor, PsiFile psiFile) {
        if (controller.state().currentFileName() == null) {
            prompts.showSelectTraceMessage(project);
            return;
        }

        TraceNode source;
        try {
            source = captureService.captureCurrentLine(project, editor, psiFile);
        } catch (IllegalArgumentException exception) {
            prompts.showCaptureError(project, exception.getMessage());
            return;
        }

        int sourceIndex = controller.addOrReuseNode(source);
        Optional<TraceNode> detectedTarget = captureService.detectTarget(project, editor, psiFile);
        if (detectedTarget.isPresent()
                && prompts.confirmDetectedLink(project, source.displayName(), detectedTarget.get().displayName())) {
            int targetIndex = controller.addOrReuseNode(detectedTarget.get());
            String sourceId = controller.state().currentDocument().nodes().get(sourceIndex).id();
            String targetId = controller.state().currentDocument().nodes().get(targetIndex).id();
            try {
                controller.setPendingLinkSource(sourceId);
                controller.linkPendingSourceTo(targetId, TraceLinkKind.DETECTED);
            } catch (IllegalArgumentException exception) {
                prompts.showLinkError(project, exception.getMessage());
            }
        }
        refreshUi.run();
    }
```

把 `src/main/java/com/zimaai/codetrace/actions/PsiTraceNodeCaptureService.java` 的三个方法改成：

```java
    @Override
    public TraceNode captureCurrentLine(Project project, Editor editor, PsiFile psiFile) {
        PsiElement callable = findCallableOwner(psiFile.findElementAt(editor.getCaretModel().getOffset()));
        int zeroBasedLine = editor.getCaretModel().getLogicalPosition().line;
        return buildNodeFromLine(project, psiFile, editor.getDocument(), zeroBasedLine, callable);
    }

    @Override
    public Optional<TraceNode> detectTarget(Project project, Editor editor, PsiFile psiFile) {
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
            return Optional.of(buildNodeFromLine(project, target.getContainingFile(), document, zeroBasedLine, target));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static TraceNode buildNodeFromLine(
            Project project,
            PsiFile psiFile,
            Document document,
            int zeroBasedLine,
            PsiElement callable) {
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
        String storedFilePath = resolveStoredFilePath(project, file);
        return new TraceNode(
                "node-" + UUID.randomUUID(),
                lineText,
                qualifiedName,
                signature,
                storedFilePath,
                safeLine + 1,
                psiFile.getLanguage().getID(),
                "",
                navigationHint);
    }

    private static String resolveStoredFilePath(Project project, VirtualFile file) {
        if (file == null) {
            return "";
        }
        String projectBasePath = project == null ? null : project.getBasePath();
        if (projectBasePath == null || projectBasePath.isBlank()) {
            throw new IllegalStateException("Project base path is unavailable");
        }
        return TraceNodePathResolver.toStoredPath(Path.of(projectBasePath), Path.of(file.getPath()));
    }
```

同时在 `PsiTraceNodeCaptureService.java` 顶部补齐导入：

```java
import com.zimaai.codetrace.navigation.TraceNodePathResolver;
import java.nio.file.Path;
```

- [ ] **步骤 4：运行测试确认通过**

运行：

```powershell
.\gradlew.bat test --tests "com.zimaai.codetrace.actions.AddToCodeTraceHandlerTest"
```

预期：PASS。

- [ ] **步骤 5：Commit**

```powershell
git add src/main/java/com/zimaai/codetrace/actions/PsiTraceNodeCaptureService.java src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceHandler.java src/main/java/com/zimaai/codetrace/actions/TraceUserPrompts.java src/main/java/com/zimaai/codetrace/actions/SwingTraceUserPrompts.java src/test/java/com/zimaai/codetrace/actions/AddToCodeTraceHandlerTest.java
git commit -m "feat: store relative file paths for captured nodes"
```

## Task 3: 让导航兼容相对路径和历史绝对路径

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/navigation/CodeNavigationService.java`
- 创建：`src/test/java/com/zimaai/codetrace/navigation/CodeNavigationServiceTest.java`

- [ ] **步骤 1：编写失败的导航测试**

创建 `src/test/java/com/zimaai/codetrace/navigation/CodeNavigationServiceTest.java`：

```java
package com.zimaai.codetrace.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.zimaai.codetrace.model.TraceNode;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeNavigationServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesRelativePathAgainstProjectRootBeforeOpening() {
        Path projectRoot = tempDir.resolve("project").toAbsolutePath().normalize();
        AtomicReference<String> lookedUpPath = new AtomicReference<>();
        AtomicInteger openedLine = new AtomicInteger(-1);

        CodeNavigationService service = new CodeNavigationService(
                projectWithBasePath(projectRoot),
                path -> {
                    lookedUpPath.set(path);
                    return new LightVirtualFile("AuthController.java", "");
                },
                (file, line) -> openedLine.set(line));

        boolean navigated = service.navigate(new TraceNode(
                "node-1",
                "return authService.login(user);",
                "AuthController#login",
                "login(User user)",
                "src/AuthController.java",
                21,
                "JAVA",
                "",
                "AuthController#login(User)"));

        assertTrue(navigated);
        assertEquals(projectRoot.resolve("src/AuthController.java").toString().replace('\\', '/'), lookedUpPath.get());
        assertEquals(20, openedLine.get());
    }

    @Test
    void keepsAbsolutePathUnchangedBeforeOpening() {
        Path projectRoot = tempDir.resolve("project").toAbsolutePath().normalize();
        Path absoluteFile = tempDir.resolve("legacy").resolve("AuthController.java").toAbsolutePath().normalize();
        AtomicReference<String> lookedUpPath = new AtomicReference<>();

        CodeNavigationService service = new CodeNavigationService(
                projectWithBasePath(projectRoot),
                path -> {
                    lookedUpPath.set(path);
                    return new LightVirtualFile("AuthController.java", "");
                },
                (file, line) -> {
                });

        boolean navigated = service.navigate(new TraceNode(
                "node-1",
                "return authService.login(user);",
                "AuthController#login",
                "login(User user)",
                absoluteFile.toString(),
                21,
                "JAVA",
                "",
                "AuthController#login(User)"));

        assertTrue(navigated);
        assertEquals(absoluteFile.toString().replace('\\', '/'), lookedUpPath.get());
    }

    @Test
    void returnsFalseWhenResolvedFileCannotBeFound() {
        Path projectRoot = tempDir.resolve("project").toAbsolutePath().normalize();
        CodeNavigationService service = new CodeNavigationService(
                projectWithBasePath(projectRoot),
                path -> null,
                (file, line) -> {
                });

        boolean navigated = service.navigate(new TraceNode(
                "node-1",
                "return authService.login(user);",
                "AuthController#login",
                "login(User user)",
                "src/Missing.java",
                21,
                "JAVA",
                "",
                "AuthController#login(User)"));

        assertFalse(navigated);
    }

    private static Project projectWithBasePath(Path basePath) {
        return (Project) Proxy.newProxyInstance(
                Project.class.getClassLoader(),
                new Class<?>[] {Project.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBasePath" -> basePath.toString();
                    case "isDisposed" -> false;
                    default -> defaultValue(method.getReturnType());
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == double.class) {
            return 0.0d;
        }
        if (type == char.class) {
            return '\0';
        }
        throw new IllegalArgumentException("Unsupported primitive: " + type.getName());
    }
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：

```powershell
.\gradlew.bat test --tests "com.zimaai.codetrace.navigation.CodeNavigationServiceTest"
```

预期：FAIL，编译错误指出 `CodeNavigationService(Project, Function<String, VirtualFile>, BiConsumer<VirtualFile, Integer>)` 构造不存在。

- [ ] **步骤 3：编写最少实现代码**

把 `src/main/java/com/zimaai/codetrace/navigation/CodeNavigationService.java` 改成：

```java
package com.zimaai.codetrace.navigation;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.zimaai.codetrace.model.TraceNode;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class CodeNavigationService {
    private final Project project;
    private final Function<String, VirtualFile> fileFinder;
    private final BiConsumer<VirtualFile, Integer> opener;

    public CodeNavigationService(Project project) {
        this(
                project,
                path -> LocalFileSystem.getInstance().findFileByPath(path),
                (file, line) -> new OpenFileDescriptor(project, file, line, 0).navigate(true));
    }

    CodeNavigationService(
            Project project,
            Function<String, VirtualFile> fileFinder,
            BiConsumer<VirtualFile, Integer> opener) {
        this.project = Objects.requireNonNull(project, "project");
        this.fileFinder = Objects.requireNonNull(fileFinder, "fileFinder");
        this.opener = Objects.requireNonNull(opener, "opener");
    }

    public boolean navigate(TraceNode node) {
        if (node == null || node.filePath() == null || node.filePath().isBlank()) {
            return false;
        }
        String path = TraceNodePathResolver.resolveForNavigation(requireProjectRoot(), node.filePath());
        VirtualFile virtualFile = fileFinder.apply(path);
        if (virtualFile == null) {
            return false;
        }
        int line = Math.max(node.line() - 1, 0);
        opener.accept(virtualFile, line);
        return true;
    }

    private Path requireProjectRoot() {
        String basePath = project.getBasePath();
        if (basePath == null || basePath.isBlank()) {
            throw new IllegalStateException("Project base path is unavailable");
        }
        return Path.of(basePath);
    }
}
```

- [ ] **步骤 4：运行测试确认通过**

运行：

```powershell
.\gradlew.bat test --tests "com.zimaai.codetrace.navigation.CodeNavigationServiceTest" --tests "com.zimaai.codetrace.actions.AddToCodeTraceHandlerTest" --tests "com.zimaai.codetrace.navigation.TraceNodePathResolverTest"
```

预期：PASS。

- [ ] **步骤 5：Commit**

```powershell
git add src/main/java/com/zimaai/codetrace/navigation/CodeNavigationService.java src/test/java/com/zimaai/codetrace/navigation/CodeNavigationServiceTest.java
git commit -m "feat: resolve relative trace node file paths on navigation"
```

## Task 4: 更新人工冒烟清单并跑完整回归

**文件：**
- 修改：`docs/superpowers/plans/2026-05-29-code-trace-manual-smoke-checklist.md`

- [ ] **步骤 1：补充相对路径/绝对路径兼容的人工检查项**

把 `docs/superpowers/plans/2026-05-29-code-trace-manual-smoke-checklist.md` 的 `Editor Popup Action` 和 `Navigation` 部分改成：

```markdown
## Editor Popup Action

1. Right-click in an in-project editor file and find `Add to code-trace`.
2. Ensure a JSON is selected in Tool Window before triggering the action.
3. Current line is added as a node and matches editor line text.
4. Open the saved `code-trace/*.json` file and confirm the new node `filePath` is project-relative, for example `src/main/java/...`.
5. If target confirmation appears and you choose `Yes`, target node and `DETECTED` link are created.
6. Trigger the action from a file outside the current project root and confirm a rejection message appears and the trace content does not change.

## Navigation

1. Double-click a node whose `filePath` is relative and verify editor navigates to the correct file and line.
2. Edit one node JSON manually so `filePath` becomes an absolute path, refresh the Tool Window, and verify double-click navigation still works.
3. If code moved externally, failure is user-visible and tool window remains stable.
```

- [ ] **步骤 2：运行完整测试套件**

运行：

```powershell
.\gradlew.bat test
```

预期：PASS，输出 `BUILD SUCCESSFUL`。

- [ ] **步骤 3：Commit**

```powershell
git add docs/superpowers/plans/2026-05-29-code-trace-manual-smoke-checklist.md
git commit -m "docs: add relative node file path smoke checks"
```

## Spec Coverage Check

- `Path Storage Rules` 对应 Task 1 和 Task 2：相对路径转换、`/` 分隔符标准化、项目外 source 拒绝。
- `Capture Flow` 对应 Task 2：source capture 抛错并提示；项目外 detected target 被忽略而不是入库。
- `Navigation Resolution Rules` 对应 Task 1 和 Task 3：相对路径拼接项目根目录、绝对路径保持兼容。
- `External File Behavior` 对应 Task 2 和 Task 4：新采集拒绝，旧绝对路径仍可导航，人工回归覆盖这两个入口。
- `Data Model And Persistence Boundaries` 对应 Task 1：`TraceJsonMapper` 只加回归断言，不引入 schema 变化。

## Placeholder Scan

- 没有 `TODO`、`TBD`、`后续实现`、`类似任务 N`。
- 每个代码任务都给出了具体文件、测试代码、运行命令和 commit 命令。
- 所有新增方法名在任务间保持一致：`TraceNodePathResolver.toStoredPath(...)`、`resolveForNavigation(...)`、`TraceUserPrompts.showCaptureError(...)`。
