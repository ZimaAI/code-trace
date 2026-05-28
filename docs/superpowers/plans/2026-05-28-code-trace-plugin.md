# Code Trace Plugin Implementation Plan

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 构建一个 IntelliJ IDEA `code-trace` 插件，在 Tool Window 中支持手动录制代码导航链路、管理项目根目录 `code-trace/` 下的 JSON 文件、编辑流程备注和节点备注，并支持点击节点跳转回代码。

**架构：** 插件分为五层：Tool Window UI、状态控制器、磁盘存储服务、录制服务、代码导航服务。录制只在显式开始/结束之间生效，生成新的 `current` 版本并将旧 `current` 归档到 `history`。JSON 存储作为唯一事实来源，UI 通过控制器协调保存、刷新、冲突确认和导航动作。

**技术栈：** Java 21、Swing UI、IntelliJ Platform Gradle Plugin `org.jetbrains.intellij.platform` 2.x、IntelliJ Platform SDK、Jackson、JUnit 5、IntelliJ Platform test framework

---

## Planned File Structure

### Build and plugin metadata

- 修改：`build.gradle.kts`
  - 迁移到 IntelliJ Platform 2.x 插件构建，声明 IDE 平台依赖、测试框架、Jackson。
- 修改：`settings.gradle.kts`
  - 增加 pluginManagement 仓库，确保 IntelliJ Platform Gradle Plugin 可解析。
- 创建：`gradle.properties`
  - 固定 plugin id、plugin name、IDE 版本、Java 版本。
- 修改：`.gitignore`
  - 忽略 IntelliJ Platform sandbox 和测试产物目录。
- 创建：`src/main/resources/META-INF/plugin.xml`
  - 注册 Tool Window 和项目级服务。

### Domain and storage

- 创建：`src/main/java/com/zimaai/codetrace/model/TraceDocument.java`
- 创建：`src/main/java/com/zimaai/codetrace/model/TraceVersion.java`
- 创建：`src/main/java/com/zimaai/codetrace/model/TraceNode.java`
- 创建：`src/main/java/com/zimaai/codetrace/model/TraceVersionSource.java`
- 创建：`src/main/java/com/zimaai/codetrace/storage/TraceJsonMapper.java`
- 创建：`src/main/java/com/zimaai/codetrace/storage/TraceStorageService.java`
- 创建：`src/main/java/com/zimaai/codetrace/storage/TraceStorageException.java`
- 创建：`src/main/java/com/zimaai/codetrace/storage/TraceProjectPaths.java`

### Recording and navigation

- 创建：`src/main/java/com/zimaai/codetrace/recording/TraceRecordingService.java`
- 创建：`src/main/java/com/zimaai/codetrace/recording/TraceRecordingSession.java`
- 创建：`src/main/java/com/zimaai/codetrace/recording/TraceableNavigationTarget.java`
- 创建：`src/main/java/com/zimaai/codetrace/recording/IdeNavigationListener.java`
- 创建：`src/main/java/com/zimaai/codetrace/navigation/CodeNavigationService.java`

### Controller and UI

- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceToolWindowFactory.java`
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java`
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java`
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/TraceFileListPanel.java`
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java`
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/HistoryListPanel.java`
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/UnsavedChangesDecision.java`

### Tests

- 创建：`src/test/java/com/zimaai/codetrace/build/PluginDescriptorTest.java`
- 创建：`src/test/java/com/zimaai/codetrace/storage/TraceJsonMapperTest.java`
- 创建：`src/test/java/com/zimaai/codetrace/storage/TraceStorageServiceTest.java`
- 创建：`src/test/java/com/zimaai/codetrace/storage/TestProjectFactory.java`
- 创建：`src/test/java/com/zimaai/codetrace/recording/TraceRecordingServiceTest.java`
- 创建：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java`
- 创建：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceToolWindowIntegrationTest.java`
- 创建：`src/test/java/com/zimaai/codetrace/navigation/CodeNavigationServiceIntegrationTest.java`

## Task 1: Bootstrap the IntelliJ plugin project

**文件：**
- 修改：`build.gradle.kts`
- 修改：`settings.gradle.kts`
- 创建：`gradle.properties`
- 修改：`.gitignore`
- 创建：`src/main/resources/META-INF/plugin.xml`
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceToolWindowFactory.java`
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
- 测试：`src/test/java/com/zimaai/codetrace/build/PluginDescriptorTest.java`

- [ ] **步骤 1：编写失败的插件描述测试**

```java
package com.zimaai.codetrace.build;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PluginDescriptorTest {
    @Test
    void pluginXmlRegistersCodeTraceToolWindow() throws IOException {
        try (var stream = getClass().getResourceAsStream("/META-INF/plugin.xml")) {
            assertTrue(stream != null, "plugin.xml should exist");
            String xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("id=\"com.zimaai.codetrace\""));
            assertTrue(xml.contains("com.intellij.toolWindow"));
            assertTrue(xml.contains("anchor=\"left\""));
            assertTrue(xml.contains("factoryClass=\"com.zimaai.codetrace.toolwindow.CodeTraceToolWindowFactory\""));
        }
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.build.PluginDescriptorTest`

预期：FAIL，提示 `/META-INF/plugin.xml` 不存在。

- [ ] **步骤 3：迁移构建配置并创建最小插件骨架**

`build.gradle.kts`：

```kotlin
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.16.0"
}

group = "com.zimaai.codetrace"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")

    testImplementation(platform("org.junit:junit-bom:5.12.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")

    intellijPlatform {
        intellijIdeaCommunity("2026.1.2")
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
    }
}

tasks.test {
    useJUnitPlatform()
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
        }
    }
}
```

`settings.gradle.kts`：

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "code-trace"
```

`gradle.properties`：

```properties
pluginGroup = com.zimaai.codetrace
pluginName = code-trace
platformVersion = 2026.1.2
javaVersion = 21
```

`.gitignore` 追加：

```gitignore
.intellijPlatform/
sandbox/
```

`src/main/resources/META-INF/plugin.xml`：

```xml
<idea-plugin>
    <id>com.zimaai.codetrace</id>
    <name>code-trace</name>
    <vendor email="support@example.com">zimaai</vendor>

    <depends>com.intellij.modules.platform</depends>

    <extensions defaultExtensionNs="com.intellij">
        <toolWindow
                id="code-trace"
                anchor="left"
                factoryClass="com.zimaai.codetrace.toolwindow.CodeTraceToolWindowFactory"
                icon="AllIcons.Toolwindows.ToolWindowMessages"/>
    </extensions>
</idea-plugin>
```

`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceToolWindowFactory.java`：

```java
package com.zimaai.codetrace.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public final class CodeTraceToolWindowFactory implements ToolWindowFactory {
    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        CodeTracePanel panel = new CodeTracePanel(project);
        var content = ContentFactory.getInstance().createContent(panel.getComponent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
```

`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`：

```java
package com.zimaai.codetrace.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import java.awt.BorderLayout;
import javax.swing.JComponent;

public final class CodeTracePanel {
    private final JBPanel<?> root;

    public CodeTracePanel(Project project) {
        root = new JBPanel<>(new BorderLayout());
        root.add(new JBLabel("code-trace"), BorderLayout.CENTER);
    }

    public JComponent getComponent() {
        return root;
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.build.PluginDescriptorTest`

预期：PASS，`PluginDescriptorTest` 通过。

- [ ] **步骤 5：Commit**

```bash
git add build.gradle.kts settings.gradle.kts gradle.properties .gitignore src/main/resources/META-INF/plugin.xml src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceToolWindowFactory.java src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java src/test/java/com/zimaai/codetrace/build/PluginDescriptorTest.java
git commit -m "build: bootstrap intellij plugin skeleton"
```

## Task 2: Define the trace JSON schema and mapper

**文件：**
- 创建：`src/main/java/com/zimaai/codetrace/model/TraceDocument.java`
- 创建：`src/main/java/com/zimaai/codetrace/model/TraceVersion.java`
- 创建：`src/main/java/com/zimaai/codetrace/model/TraceNode.java`
- 创建：`src/main/java/com/zimaai/codetrace/model/TraceVersionSource.java`
- 创建：`src/main/java/com/zimaai/codetrace/storage/TraceJsonMapper.java`
- 测试：`src/test/java/com/zimaai/codetrace/storage/TraceJsonMapperTest.java`

- [ ] **步骤 1：编写失败的 JSON round-trip 测试**

```java
package com.zimaai.codetrace.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.model.TraceVersion;
import com.zimaai.codetrace.model.TraceVersionSource;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TraceJsonMapperTest {
    @Test
    void writesAndReadsCurrentAndHistoryVersions() throws Exception {
        TraceNode node = new TraceNode(
                "node-1",
                "login()",
                "com.example.AuthService.login",
                "login(String username)",
                "src/main/java/com/example/AuthService.java",
                42,
                "JAVA",
                "entry point",
                "com.example.AuthService#login(String)");
        TraceVersion history = new TraceVersion(
                "v1",
                TraceVersionSource.RECORDING,
                Instant.parse("2026-05-28T10:00:00Z"),
                Instant.parse("2026-05-28T10:00:00Z"),
                true,
                List.of(node));
        TraceDocument document = new TraceDocument(
                1,
                "trace-auth-login",
                "Auth Login",
                "trace note",
                Instant.parse("2026-05-28T09:00:00Z"),
                Instant.parse("2026-05-28T10:00:00Z"),
                history,
                List.of(history));

        TraceJsonMapper mapper = new TraceJsonMapper();
        String json = mapper.write(document);
        TraceDocument restored = mapper.read(json);

        assertTrue(json.contains("\"schemaVersion\" : 1"));
        assertEquals("Auth Login", restored.name());
        assertEquals("entry point", restored.current().nodes().get(0).note());
        assertEquals(1, restored.history().size());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.storage.TraceJsonMapperTest`

预期：FAIL，提示 `TraceDocument`、`TraceJsonMapper` 等类型不存在。

- [ ] **步骤 3：实现模型和 JSON 映射**

`src/main/java/com/zimaai/codetrace/model/TraceVersionSource.java`：

```java
package com.zimaai.codetrace.model;

public enum TraceVersionSource {
    MANUAL,
    RECORDING
}
```

`src/main/java/com/zimaai/codetrace/model/TraceNode.java`：

```java
package com.zimaai.codetrace.model;

public record TraceNode(
        String id,
        String displayName,
        String qualifiedName,
        String signature,
        String filePath,
        int line,
        String language,
        String note,
        String navigationHint) {
}
```

`src/main/java/com/zimaai/codetrace/model/TraceVersion.java`：

```java
package com.zimaai.codetrace.model;

import java.time.Instant;
import java.util.List;

public record TraceVersion(
        String versionId,
        TraceVersionSource source,
        Instant recordedAt,
        Instant updatedAt,
        boolean nodeDedupEnabled,
        List<TraceNode> nodes) {
}
```

`src/main/java/com/zimaai/codetrace/model/TraceDocument.java`：

```java
package com.zimaai.codetrace.model;

import java.time.Instant;
import java.util.List;

public record TraceDocument(
        int schemaVersion,
        String id,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt,
        TraceVersion current,
        List<TraceVersion> history) {
}
```

`src/main/java/com/zimaai/codetrace/storage/TraceJsonMapper.java`：

```java
package com.zimaai.codetrace.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zimaai.codetrace.model.TraceDocument;

public final class TraceJsonMapper {
    private final ObjectMapper mapper;

    public TraceJsonMapper() {
        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public String write(TraceDocument document) throws Exception {
        return mapper.writeValueAsString(document);
    }

    public TraceDocument read(String json) throws Exception {
        return mapper.readValue(json, TraceDocument.class);
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.storage.TraceJsonMapperTest`

预期：PASS，JSON round-trip 测试通过。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/model src/main/java/com/zimaai/codetrace/storage/TraceJsonMapper.java src/test/java/com/zimaai/codetrace/storage/TraceJsonMapperTest.java
git commit -m "feat: define trace json schema"
```

## Task 3: Implement project-root storage and file management

**文件：**
- 创建：`src/main/java/com/zimaai/codetrace/storage/TraceStorageService.java`
- 创建：`src/main/java/com/zimaai/codetrace/storage/TraceStorageException.java`
- 创建：`src/main/java/com/zimaai/codetrace/storage/TraceProjectPaths.java`
- 测试：`src/test/java/com/zimaai/codetrace/storage/TraceStorageServiceTest.java`

- [ ] **步骤 1：编写失败的文件管理测试**

```java
package com.zimaai.codetrace.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.intellij.mock.MockProject;
import com.intellij.openapi.util.Disposer;
import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceVersion;
import com.zimaai.codetrace.model.TraceVersionSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TraceStorageServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void createsLoadsCopiesRenamesAndDeletesTraceFiles() throws Exception {
        var disposable = Disposer.newDisposable();
        try {
            MockProject project = TestProjectFactory.projectAt(tempDir, disposable);
            TraceStorageService storage = new TraceStorageService(project, new TraceJsonMapper());
            TraceDocument document = new TraceDocument(
                    1, "trace-1", "Trace 1", "note",
                    Instant.parse("2026-05-28T10:00:00Z"),
                    Instant.parse("2026-05-28T10:00:00Z"),
                    new TraceVersion("v1", TraceVersionSource.MANUAL, Instant.parse("2026-05-28T10:00:00Z"),
                            Instant.parse("2026-05-28T10:00:00Z"), true, List.of()),
                    List.of());

            storage.save("trace-1.json", document);
            assertTrue(Files.exists(tempDir.resolve("code-trace").resolve("trace-1.json")));
            assertEquals(1, storage.listFiles().size());

            storage.copy("trace-1.json", "trace-1-copy.json");
            storage.rename("trace-1-copy.json", "trace-1-renamed.json");
            storage.delete("trace-1-renamed.json");

            assertEquals(List.of("trace-1.json"), storage.listFiles());
        } finally {
            Disposer.dispose(disposable);
        }
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.storage.TraceStorageServiceTest`

预期：FAIL，提示 `TraceStorageService` 和 `TestProjectFactory` 不存在。

- [ ] **步骤 3：实现项目路径和文件管理服务**

`src/main/java/com/zimaai/codetrace/storage/TraceProjectPaths.java`：

```java
package com.zimaai.codetrace.storage;

import com.intellij.openapi.project.Project;
import java.nio.file.Path;

public final class TraceProjectPaths {
    private final Project project;

    public TraceProjectPaths(Project project) {
        this.project = project;
    }

    public Path traceDirectory() {
        return Path.of(project.getBasePath()).resolve("code-trace");
    }
}
```

`src/main/java/com/zimaai/codetrace/storage/TraceStorageException.java`：

```java
package com.zimaai.codetrace.storage;

public final class TraceStorageException extends RuntimeException {
    public TraceStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

`src/main/java/com/zimaai/codetrace/storage/TraceStorageService.java`：

```java
package com.zimaai.codetrace.storage;

import com.intellij.openapi.project.Project;
import com.zimaai.codetrace.model.TraceDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class TraceStorageService {
    private final TraceProjectPaths paths;
    private final TraceJsonMapper mapper;

    public TraceStorageService(Project project, TraceJsonMapper mapper) {
        this.paths = new TraceProjectPaths(project);
        this.mapper = mapper;
    }

    public List<String> listFiles() {
        try {
            ensureDirectory();
            try (var stream = Files.list(paths.traceDirectory())) {
                return stream
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .map(path -> path.getFileName().toString())
                        .sorted()
                        .toList();
            }
        } catch (IOException exception) {
            throw new TraceStorageException("Failed to list trace files", exception);
        }
    }

    public TraceDocument load(String fileName) {
        try {
            String json = Files.readString(resolve(fileName));
            return mapper.read(json);
        } catch (Exception exception) {
            throw new TraceStorageException("Failed to load trace file: " + fileName, exception);
        }
    }

    public void save(String fileName, TraceDocument document) {
        try {
            ensureDirectory();
            Files.writeString(resolve(fileName), mapper.write(document));
        } catch (Exception exception) {
            throw new TraceStorageException("Failed to save trace file: " + fileName, exception);
        }
    }

    public void rename(String oldFileName, String newFileName) {
        try {
            Files.move(resolve(oldFileName), resolve(newFileName));
        } catch (IOException exception) {
            throw new TraceStorageException("Failed to rename trace file", exception);
        }
    }

    public void copy(String sourceFileName, String targetFileName) {
        try {
            Files.copy(resolve(sourceFileName), resolve(targetFileName));
        } catch (IOException exception) {
            throw new TraceStorageException("Failed to copy trace file", exception);
        }
    }

    public void delete(String fileName) {
        try {
            Files.deleteIfExists(resolve(fileName));
        } catch (IOException exception) {
            throw new TraceStorageException("Failed to delete trace file", exception);
        }
    }

    private void ensureDirectory() throws IOException {
        Files.createDirectories(paths.traceDirectory());
    }

    private Path resolve(String fileName) {
        return paths.traceDirectory().resolve(fileName);
    }
}
```

测试辅助：`src/test/java/com/zimaai/codetrace/storage/TestProjectFactory.java`

```java
package com.zimaai.codetrace.storage;

import com.intellij.mock.MockProject;
import com.intellij.openapi.Disposable;
import java.lang.reflect.Field;
import java.nio.file.Path;

final class TestProjectFactory {
    private TestProjectFactory() {
    }

    static MockProject projectAt(Path basePath, Disposable disposable) throws Exception {
        MockProject project = new MockProject(null, disposable);
        Field basePathField = project.getClass().getSuperclass().getDeclaredField("myBasePath");
        basePathField.setAccessible(true);
        basePathField.set(project, basePath.toString());
        return project;
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.storage.TraceStorageServiceTest`

预期：PASS，文件创建、复制、重命名、删除和列表读取全部通过。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/storage src/test/java/com/zimaai/codetrace/storage
git commit -m "feat: add trace storage service"
```

## Task 4: Implement recording domain rules

**文件：**
- 创建：`src/main/java/com/zimaai/codetrace/recording/TraceableNavigationTarget.java`
- 创建：`src/main/java/com/zimaai/codetrace/recording/TraceRecordingSession.java`
- 创建：`src/main/java/com/zimaai/codetrace/recording/TraceRecordingService.java`
- 测试：`src/test/java/com/zimaai/codetrace/recording/TraceRecordingServiceTest.java`

- [ ] **步骤 1：编写失败的录制规则测试**

```java
package com.zimaai.codetrace.recording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.model.TraceVersion;
import com.zimaai.codetrace.model.TraceVersionSource;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TraceRecordingServiceTest {
    @Test
    void archivesPreviousCurrentAndSuppressesConsecutiveDuplicates() {
        TraceRecordingService service = new TraceRecordingService(
                java.time.Clock.fixed(
                        Instant.parse("2026-05-28T10:30:00Z"),
                        java.time.ZoneOffset.UTC));
        TraceDocument original = new TraceDocument(
                1, "trace-1", "Trace 1", "note",
                Instant.parse("2026-05-28T10:00:00Z"),
                Instant.parse("2026-05-28T10:00:00Z"),
                new TraceVersion("v1", TraceVersionSource.MANUAL,
                        Instant.parse("2026-05-28T10:00:00Z"),
                        Instant.parse("2026-05-28T10:00:00Z"),
                        true,
                        List.of()),
                List.of());

        service.start(true);
        service.record(new TraceableNavigationTarget("a", "A", "A#a()", "a()", "A.java", 1, "JAVA", "A#a()"));
        service.record(new TraceableNavigationTarget("a", "A", "A#a()", "a()", "A.java", 1, "JAVA", "A#a()"));
        service.record(new TraceableNavigationTarget("b", "B", "B#b()", "b()", "B.java", 2, "JAVA", "B#b()"));

        TraceDocument updated = service.stop(original);

        assertEquals(2, updated.current().nodes().size());
        assertEquals("A", updated.current().nodes().get(0).displayName());
        assertEquals(1, updated.history().size());
        assertSame(original.current(), updated.history().get(0));
        assertEquals(TraceVersionSource.RECORDING, updated.current().source());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.recording.TraceRecordingServiceTest`

预期：FAIL，提示录制相关类型不存在。

- [ ] **步骤 3：实现录制会话和归档规则**

`src/main/java/com/zimaai/codetrace/recording/TraceableNavigationTarget.java`：

```java
package com.zimaai.codetrace.recording;

public record TraceableNavigationTarget(
        String id,
        String displayName,
        String qualifiedName,
        String signature,
        String filePath,
        int line,
        String language,
        String navigationHint) {
}
```

`src/main/java/com/zimaai/codetrace/recording/TraceRecordingSession.java`：

```java
package com.zimaai.codetrace.recording;

import com.zimaai.codetrace.model.TraceNode;
import java.util.ArrayList;
import java.util.List;

final class TraceRecordingSession {
    private final boolean suppressConsecutiveDuplicates;
    private final List<TraceNode> nodes = new ArrayList<>();

    TraceRecordingSession(boolean suppressConsecutiveDuplicates) {
        this.suppressConsecutiveDuplicates = suppressConsecutiveDuplicates;
    }

    void record(TraceableNavigationTarget target) {
        TraceNode next = new TraceNode(
                target.id(),
                target.displayName(),
                target.qualifiedName(),
                target.signature(),
                target.filePath(),
                target.line(),
                target.language(),
                "",
                target.navigationHint());
        if (suppressConsecutiveDuplicates && !nodes.isEmpty()) {
            TraceNode last = nodes.get(nodes.size() - 1);
            if (last.qualifiedName().equals(next.qualifiedName())
                    && last.filePath().equals(next.filePath())
                    && last.line() == next.line()) {
                return;
            }
        }
        nodes.add(next);
    }

    List<TraceNode> snapshot() {
        return List.copyOf(nodes);
    }
}
```

`src/main/java/com/zimaai/codetrace/recording/TraceRecordingService.java`：

```java
package com.zimaai.codetrace.recording;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceVersion;
import com.zimaai.codetrace.model.TraceVersionSource;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TraceRecordingService {
    private final Clock clock;
    private TraceRecordingSession session;

    public TraceRecordingService(Clock clock) {
        this.clock = clock;
    }

    public TraceRecordingService(java.util.function.Supplier<Instant> supplier) {
        this(Clock.fixed(supplier.get(), java.time.ZoneOffset.UTC));
    }

    public void start(boolean suppressConsecutiveDuplicates) {
        session = new TraceRecordingSession(suppressConsecutiveDuplicates);
    }

    public void record(TraceableNavigationTarget target) {
        if (session != null) {
            session.record(target);
        }
    }

    public TraceDocument stop(TraceDocument document) {
        Instant now = clock.instant();
        TraceVersion nextVersion = new TraceVersion(
                "v-" + UUID.randomUUID(),
                TraceVersionSource.RECORDING,
                now,
                now,
                true,
                session == null ? List.of() : session.snapshot());
        List<TraceVersion> history = new ArrayList<>(document.history());
        history.add(document.current());
        session = null;
        return new TraceDocument(
                document.schemaVersion(),
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                nextVersion,
                List.copyOf(history));
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.recording.TraceRecordingServiceTest`

预期：PASS，归档历史和连续重复去重规则通过。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/recording src/test/java/com/zimaai/codetrace/recording
git commit -m "feat: add trace recording rules"
```

## Task 5: Implement Tool Window controller state flow

**文件：**
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java`
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java`
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/UnsavedChangesDecision.java`
- 测试：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java`

- [ ] **步骤 1：编写失败的控制器状态测试**

```java
package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceVersion;
import com.zimaai.codetrace.model.TraceVersionSource;
import com.intellij.openapi.util.Disposer;
import com.zimaai.codetrace.navigation.CodeNavigationService;
import com.zimaai.codetrace.recording.TraceRecordingService;
import com.zimaai.codetrace.storage.TestProjectFactory;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class CodeTraceControllerTest {
    @TempDir
    Path tempDir;

    @Test
    void refreshPromptsWhenDocumentIsDirty() throws Exception {
        var disposable = Disposer.newDisposable();
        try {
            var project = TestProjectFactory.projectAt(tempDir, disposable);
            var storage = new com.zimaai.codetrace.storage.TraceStorageService(
                    project,
                    new com.zimaai.codetrace.storage.TraceJsonMapper());
            TraceDocument persisted = document();
            storage.save("trace-1.json", persisted);

            CodeTraceController controller = new CodeTraceController(
                    storage,
                    decision -> decision == UnsavedChangesDecision.DISCARD,
                    new TraceRecordingService(java.time.Clock.systemUTC()),
                    new CodeNavigationService(project));

            controller.loadFile("trace-1.json", persisted);
        
        controller.updateDescription("changed");
        boolean refreshed = controller.refreshCurrentFile();

        assertTrue(refreshed);
        assertEquals("note", controller.state().currentDocument().description());
        assertTrue(controller.state().dirtyHistory().contains(UnsavedChangesDecision.DISCARD));
        } finally {
            Disposer.dispose(disposable);
        }
    }

    private static TraceDocument document() {
            return new TraceDocument(
                    1, "trace-1", "Trace 1", "note",
                    Instant.parse("2026-05-28T10:00:00Z"),
                    Instant.parse("2026-05-28T10:00:00Z"),
                    new TraceVersion("v1", TraceVersionSource.MANUAL,
                            Instant.parse("2026-05-28T10:00:00Z"),
                            Instant.parse("2026-05-28T10:00:00Z"),
                            true,
                            List.of()),
                    List.of());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.CodeTraceControllerTest`

预期：FAIL，提示 `CodeTraceController`、`CodeTraceState` 或测试支撑类不存在。

- [ ] **步骤 3：实现控制器状态机**

`src/main/java/com/zimaai/codetrace/toolwindow/UnsavedChangesDecision.java`：

```java
package com.zimaai.codetrace.toolwindow;

public enum UnsavedChangesDecision {
    DISCARD,
    CANCEL
}
```

`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java`：

```java
package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import java.util.ArrayList;
import java.util.List;

public final class CodeTraceState {
    private String currentFileName;
    private TraceDocument currentDocument;
    private boolean dirty;
    private final List<UnsavedChangesDecision> dirtyHistory = new ArrayList<>();

    public String currentFileName() {
        return currentFileName;
    }

    public TraceDocument currentDocument() {
        return currentDocument;
    }

    public boolean dirty() {
        return dirty;
    }

    public List<UnsavedChangesDecision> dirtyHistory() {
        return List.copyOf(dirtyHistory);
    }

    void load(String fileName, TraceDocument document) {
        this.currentFileName = fileName;
        this.currentDocument = document;
        this.dirty = false;
    }

    void markDirty(TraceDocument document) {
        this.currentDocument = document;
        this.dirty = true;
    }

    void markSaved(TraceDocument document) {
        this.currentDocument = document;
        this.dirty = false;
    }

    void recordDecision(UnsavedChangesDecision decision) {
        dirtyHistory.add(decision);
    }
}
```

`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java`：

```java
package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.storage.TraceStorageService;
import java.time.Instant;
import java.util.function.Function;

public final class CodeTraceController {
    private final TraceStorageService storage;
    private final Function<UnsavedChangesDecision, Boolean> refreshPermission;
    private final com.zimaai.codetrace.recording.TraceRecordingService recordingService;
    private final com.zimaai.codetrace.navigation.CodeNavigationService navigationService;
    private final CodeTraceState state = new CodeTraceState();

    public CodeTraceController(
            TraceStorageService storage,
            Function<UnsavedChangesDecision, Boolean> refreshPermission,
            com.zimaai.codetrace.recording.TraceRecordingService recordingService,
            com.zimaai.codetrace.navigation.CodeNavigationService navigationService) {
        this.storage = storage;
        this.refreshPermission = refreshPermission;
        this.recordingService = recordingService;
        this.navigationService = navigationService;
    }

    public void loadFile(String fileName, TraceDocument document) {
        state.load(fileName, document);
    }

    public void updateDescription(String description) {
        TraceDocument current = state.currentDocument();
        TraceDocument updated = new TraceDocument(
                current.schemaVersion(),
                current.id(),
                current.name(),
                description,
                current.createdAt(),
                Instant.now(),
                current.current(),
                current.history());
        state.markDirty(updated);
    }

    public boolean refreshCurrentFile() {
        if (state.dirty()) {
            state.recordDecision(UnsavedChangesDecision.DISCARD);
            if (!refreshPermission.apply(UnsavedChangesDecision.DISCARD)) {
                return false;
            }
        }
        state.load(state.currentFileName(), storage.load(state.currentFileName()));
        return true;
    }

    public CodeTraceState state() {
        return state;
    }

    public TraceStorageService storage() {
        return storage;
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.CodeTraceControllerTest`

预期：PASS，dirty 状态、刷新确认和 reload 行为正确。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/toolwindow src/test/java/com/zimaai/codetrace/toolwindow
git commit -m "feat: add code trace controller state flow"
```

## Task 6: Build the dual-pane Tool Window UI

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/TraceFileListPanel.java`
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java`
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/HistoryListPanel.java`
- 测试：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceToolWindowIntegrationTest.java`

- [ ] **步骤 1：编写失败的 Tool Window 布局集成测试**

```java
package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import javax.swing.JButton;

public class CodeTraceToolWindowIntegrationTest extends BasePlatformTestCase {
    public void testPanelBuildsRequiredActions() {
        CodeTracePanel panel = new CodeTracePanel(getProject());

        JButton startButton = panel.findButton("Start Recording");
        JButton refreshButton = panel.findButton("Refresh");

        assertNotNull(startButton);
        assertNotNull(refreshButton);
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.CodeTraceToolWindowIntegrationTest`

预期：FAIL，提示 `findButton` 或具体布局元素不存在。

- [ ] **步骤 3：实现双栏 Tool Window UI**

`src/main/java/com/zimaai/codetrace/toolwindow/TraceFileListPanel.java`：

```java
package com.zimaai.codetrace.toolwindow;

import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

public final class TraceFileListPanel {
    private final JBList<String> list = new JBList<>();
    private final JPanel root = new JPanel(new BorderLayout());

    public TraceFileListPanel() {
        root.add(ToolbarDecorator.createDecorator(list).createPanel(), BorderLayout.CENTER);
    }

    public JComponent component() {
        return root;
    }

    public JBList<String> list() {
        return list;
    }
}
```

`src/main/java/com/zimaai/codetrace/toolwindow/HistoryListPanel.java`：

```java
package com.zimaai.codetrace.toolwindow;

import com.intellij.ui.components.JBList;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

public final class HistoryListPanel {
    private final JBList<String> list = new JBList<>();
    private final JPanel root = new JPanel(new BorderLayout());

    public HistoryListPanel() {
        root.add(list, BorderLayout.CENTER);
    }

    public JComponent component() {
        return root;
    }
}
```

`src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java`：

```java
package com.zimaai.codetrace.toolwindow;

import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

public final class TraceEditorPanel {
    private final JBList<String> nodeList = new JBList<>();
    private final JBTextArea traceNote = new JBTextArea();
    private final JBTextArea nodeNote = new JBTextArea();
    private final JPanel root = new JPanel(new BorderLayout());

    public TraceEditorPanel() {
        JSplitPane vertical = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JBScrollPane(nodeList),
                new JBScrollPane(nodeNote));
        root.add(new JBScrollPane(traceNote), BorderLayout.NORTH);
        root.add(vertical, BorderLayout.CENTER);
    }

    public JComponent component() {
        return root;
    }
}
```

`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`：

```java
package com.zimaai.codetrace.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBScrollPane;
import java.awt.BorderLayout;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

public final class CodeTracePanel {
    private final JPanel root = new JPanel(new BorderLayout());
    private final Map<String, JButton> buttons = new HashMap<>();

    public CodeTracePanel(Project project) {
        TraceFileListPanel fileList = new TraceFileListPanel();
        TraceEditorPanel editor = new TraceEditorPanel();
        HistoryListPanel history = new HistoryListPanel();

        JPanel toolbar = new JPanel();
        addButton(toolbar, "Start Recording");
        addButton(toolbar, "Stop Recording");
        addButton(toolbar, "Save");
        addButton(toolbar, "Refresh");

        JBSplitter rightSplitter = new JBSplitter(true, 0.7f);
        rightSplitter.setFirstComponent(editor.component());
        rightSplitter.setSecondComponent(history.component());

        JBSplitter rootSplitter = new JBSplitter(false, 0.25f);
        rootSplitter.setFirstComponent(fileList.component());
        rootSplitter.setSecondComponent(rightSplitter);

        root.add(toolbar, BorderLayout.NORTH);
        root.add(rootSplitter, BorderLayout.CENTER);
    }

    public JComponent getComponent() {
        return root;
    }

    public JButton findButton(String text) {
        return buttons.get(text);
    }

    private void addButton(JPanel toolbar, String label) {
        JButton button = new JButton(label);
        buttons.put(label, button);
        toolbar.add(button);
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.CodeTraceToolWindowIntegrationTest`

预期：PASS，Tool Window 主面板能够创建双栏布局和关键操作按钮。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/toolwindow src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceToolWindowIntegrationTest.java
git commit -m "feat: build code trace tool window layout"
```

## Task 7: Integrate IDE navigation recording and node click navigation

**文件：**
- 创建：`src/main/java/com/zimaai/codetrace/recording/IdeNavigationListener.java`
- 创建：`src/main/java/com/zimaai/codetrace/navigation/CodeNavigationService.java`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java`
- 测试：`src/test/java/com/zimaai/codetrace/navigation/CodeNavigationServiceIntegrationTest.java`

- [ ] **步骤 1：编写失败的导航定位集成测试**

```java
package com.zimaai.codetrace.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.zimaai.codetrace.model.TraceNode;

public class CodeNavigationServiceIntegrationTest extends BasePlatformTestCase {
    public void testNavigatesToTraceNodeLocation() {
        myFixture.configureByText(
                "AuthService.java",
                "class AuthService {\\n  void login() {}\\n}\\n");

        TraceNode node = new TraceNode(
                "node-1",
                "login()",
                "AuthService.login",
                "login()",
                getProject().getBasePath() + "/AuthService.java",
                2,
                "JAVA",
                "",
                "AuthService#login()");

        CodeNavigationService service = new CodeNavigationService(getProject());
        service.navigate(node);

        VirtualFile selected = FileEditorManager.getInstance(getProject()).getSelectedFiles()[0];
        assertEquals("AuthService.java", selected.getName());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.navigation.CodeNavigationServiceIntegrationTest`

预期：FAIL，提示 `CodeNavigationService` 不存在。

- [ ] **步骤 3：实现导航服务和 IDE 监听器**

`src/main/java/com/zimaai/codetrace/navigation/CodeNavigationService.java`：

```java
package com.zimaai.codetrace.navigation;

import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.zimaai.codetrace.model.TraceNode;
import java.nio.file.Path;

public final class CodeNavigationService {
    private final Project project;

    public CodeNavigationService(Project project) {
        this.project = project;
    }

    public boolean navigate(TraceNode node) {
        var virtualFile = LocalFileSystem.getInstance().findFileByPath(Path.of(node.filePath()).toString());
        if (virtualFile == null) {
            return false;
        }
        new OpenFileDescriptor(project, virtualFile, Math.max(node.line() - 1, 0), 0).navigate(true);
        return true;
    }
}
```

`src/main/java/com/zimaai/codetrace/recording/IdeNavigationListener.java`：

```java
package com.zimaai.codetrace.recording;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.Set;
import java.util.function.Consumer;

public final class IdeNavigationListener implements AnActionListener {
    private static final Set<String> TRACKED_ACTIONS = Set.of(
            "GotoDeclaration",
            "GotoImplementation",
            "GotoSuperMethod",
            "$Back",
            "$Forward");

    private final Project project;
    private final Consumer<TraceableNavigationTarget> sink;

    public IdeNavigationListener(Project project, Consumer<TraceableNavigationTarget> sink) {
        this.project = project;
        this.sink = sink;
    }

    @Override
    public void afterActionPerformed(AnAction action, AnActionEvent event, com.intellij.openapi.actionSystem.DataContext dataContext) {
        String actionId = ActionManager.getInstance().getId(action);
        if (!TRACKED_ACTIONS.contains(actionId)) {
            return;
        }
        Editor editor = event.getData(com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR);
        if (editor == null) {
            return;
        }
        var psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (psiFile == null) {
            return;
        }
        PsiElement element = psiFile.findElementAt(editor.getCaretModel().getOffset());
        VirtualFile virtualFile = psiFile.getVirtualFile();
        if (element == null || virtualFile == null) {
            return;
        }
        PsiElement methodLike = PsiTreeUtil.getParentOfType(element, PsiElement.class);
        if (methodLike == null) {
            return;
        }
        sink.accept(new TraceableNavigationTarget(
                actionId + "-" + editor.getCaretModel().getOffset(),
                methodLike.getText(),
                methodLike.toString(),
                methodLike.getText(),
                virtualFile.getPath(),
                editor.getCaretModel().getLogicalPosition().line + 1,
                methodLike.getLanguage().getID(),
                methodLike.toString()));
    }
}
```

`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java` 增量增加：

```java
private final TraceRecordingService recordingService;
private final CodeNavigationService navigationService;

public void startRecording(boolean suppressConsecutiveDuplicates) {
    recordingService.start(suppressConsecutiveDuplicates);
}

public void stopRecording() {
    TraceDocument updated = recordingService.stop(state.currentDocument());
    state.markDirty(updated);
}

public boolean navigateToNode(com.zimaai.codetrace.model.TraceNode node) {
    return navigationService.navigate(node);
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.navigation.CodeNavigationServiceIntegrationTest`

预期：PASS，点击节点时可以打开目标文件并定位到指定行。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/recording/IdeNavigationListener.java src/main/java/com/zimaai/codetrace/navigation/CodeNavigationService.java src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java src/test/java/com/zimaai/codetrace/navigation/CodeNavigationServiceIntegrationTest.java
git commit -m "feat: integrate navigation recording and jumping"
```

## Task 8: Wire persistence, refresh, and end-to-end behaviors

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceToolWindowFactory.java`
- 测试：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceToolWindowIntegrationTest.java`
- 测试：`src/test/java/com/zimaai/codetrace/navigation/CodeNavigationServiceIntegrationTest.java`

- [ ] **步骤 1：编写失败的端到端交互测试**

```java
package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.zimaai.codetrace.model.TraceDocument;
import java.nio.file.Files;
import java.nio.file.Path;

public class CodeTraceToolWindowIntegrationTest extends BasePlatformTestCase {
    public void testRefreshReloadsTraceFileFromProjectRoot() throws Exception {
        Path traceDir = Path.of(getProject().getBasePath(), "code-trace");
        Files.createDirectories(traceDir);
        Files.writeString(traceDir.resolve("trace-1.json"), """
                {
                  "schemaVersion": 1,
                  "id": "trace-1",
                  "name": "Trace 1",
                  "description": "disk note",
                  "createdAt": "2026-05-28T10:00:00Z",
                  "updatedAt": "2026-05-28T10:00:00Z",
                  "current": {
                    "versionId": "v1",
                    "source": "MANUAL",
                    "recordedAt": "2026-05-28T10:00:00Z",
                    "updatedAt": "2026-05-28T10:00:00Z",
                    "nodeDedupEnabled": true,
                    "nodes": []
                  },
                  "history": []
                }
                """);

        CodeTracePanel panel = new CodeTracePanel(getProject());
        panel.reloadFromDisk();

        TraceDocument document = panel.currentDocument();
        assertEquals("disk note", document.description());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.CodeTraceToolWindowIntegrationTest`

预期：FAIL，提示 `reloadFromDisk` 或 `currentDocument` 尚未实现。

- [ ] **步骤 3：接通 UI、控制器和存储服务**

`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceToolWindowFactory.java` 构造依赖：

```java
TraceStorageService storage = new TraceStorageService(project, new TraceJsonMapper());
TraceRecordingService recording = new TraceRecordingService(java.time.Clock.systemUTC());
CodeNavigationService navigation = new CodeNavigationService(project);
CodeTraceController controller = new CodeTraceController(
        storage,
        decision -> true,
        recording,
        navigation);
CodeTracePanel panel = new CodeTracePanel(project, controller);
```

`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java` 增量完善：

```java
public List<String> loadFileNames() {
    return storage.listFiles();
}

public void saveCurrentFile() {
    storage.save(state.currentFileName(), state.currentDocument());
    state.markSaved(state.currentDocument());
}

public void renameCurrentFile(String newFileName) {
    storage.rename(state.currentFileName(), newFileName);
    state.load(newFileName, state.currentDocument());
}

public void copyCurrentFile(String newFileName) {
    storage.copy(state.currentFileName(), newFileName);
}

public void deleteCurrentFile() {
    storage.delete(state.currentFileName());
}
```

`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java` 增量完善：

```java
private final CodeTraceController controller;

public CodeTracePanel(Project project, CodeTraceController controller) {
    this.controller = controller;
    // existing layout setup
}

public void reloadFromDisk() {
    var fileNames = controller.loadFileNames();
    if (!fileNames.isEmpty()) {
        String first = fileNames.get(0);
        controller.loadFile(first, controller.storage().load(first));
    }
}

public com.zimaai.codetrace.model.TraceDocument currentDocument() {
    return controller.state().currentDocument();
}
```

- [ ] **步骤 4：运行目标测试集验证通过**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.CodeTraceToolWindowIntegrationTest --tests com.zimaai.codetrace.navigation.CodeNavigationServiceIntegrationTest --tests com.zimaai.codetrace.storage.TraceStorageServiceTest --tests com.zimaai.codetrace.recording.TraceRecordingServiceTest`

预期：PASS，刷新、文件读写、录制归档、导航跳转全通过。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/toolwindow src/test/java/com/zimaai/codetrace/toolwindow src/test/java/com/zimaai/codetrace/navigation
git commit -m "feat: wire code trace end-to-end workflow"
```

## Task 9: Final verification and plugin smoke run

**文件：**
- 修改：`docs/superpowers/specs/2026-05-28-code-trace-plugin-design.md`
  - 仅当实现中发现规格需要补充说明时再更新。
- 测试：现有全部测试文件

- [ ] **步骤 1：运行完整测试套件**

运行：`.\gradlew.bat test`

预期：PASS，所有单元测试和平台测试通过。

- [ ] **步骤 2：运行配置校验**

运行：`.\gradlew.bat verifyPluginProjectConfiguration`

预期：PASS，构建配置、plugin.xml 和平台依赖检查通过。

- [ ] **步骤 3：启动 IDE Sandbox 做手工冒烟**

运行：`.\gradlew.bat runIde`

在沙箱 IDE 中手工验证：

- Tool Window 左侧显示 `code-trace` 文件列表
- `Start Recording` 和 `Stop Recording` 仅在录制时抓取导航链路
- 编辑 trace note 和 node note 后可保存
- 外部修改 JSON 后点击 `Refresh` 能重载
- 点击节点可以回跳到代码

- [ ] **步骤 4：记录任何与规格不一致的行为并补齐文档**

若手工冒烟发现实现与 [docs/superpowers/specs/2026-05-28-code-trace-plugin-design.md](D:/Develop/Projects/code-trace/docs/superpowers/specs/2026-05-28-code-trace-plugin-design.md:1) 不一致：

- 先修实现，再更新规格说明中对应章节
- 重新运行 `.\gradlew.bat test`

- [ ] **步骤 5：Commit**

```bash
git add .
git commit -m "test: verify code trace plugin workflow"
```
