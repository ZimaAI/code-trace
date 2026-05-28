# Code Trace 单版本节点链接改造实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将当前基于录制与版本快照的 `code-trace` 插件改造成单版本、手动加节点、显式节点链接、立即持久化的编辑器，并通过编辑器右键菜单把当前行加入当前选中的 trace。

**架构：** 存储层升级到 schema v2，`TraceDocument` 直接持有 `nodes` 和 `links`，通过 `TraceJsonMapper` 兼容读取旧版 v1 文档。控制器移除录制、历史、文件级 dirty/save，改为基于纯文档编辑器的立即持久化操作。Tool Window 只保留单版本节点列表、两个 note 保存按钮和手动链接动作，编辑器右键动作通过项目级服务复用同一个控制器和当前选中文档。

**技术栈：** Java 21、Swing、IntelliJ Platform SDK、Jackson、JUnit 5、IntelliJ Platform Gradle Plugin 2.x

---

## Planned File Structure

### Domain and migration

- 修改：`src/main/java/com/zimaai/codetrace/model/TraceDocument.java`
  - 删除旧版版本字段，改为直接持有 `nodes/links`。
- 保留：`src/main/java/com/zimaai/codetrace/model/TraceNode.java`
  - 继续承载导航元数据，`displayName` 约束改为原始单行代码。
- 创建：`src/main/java/com/zimaai/codetrace/model/TraceLink.java`
  - 表示 `sourceNodeId -> targetNodeId` 的单条链接。
- 创建：`src/main/java/com/zimaai/codetrace/model/TraceLinkKind.java`
  - 区分 `MANUAL` 和 `DETECTED`。
- 删除：`src/main/java/com/zimaai/codetrace/model/TraceVersion.java`
  - schema v2 不再保留版本对象。
- 删除：`src/main/java/com/zimaai/codetrace/model/TraceVersionSource.java`
  - schema v2 不再保留录制来源枚举。
- 修改：`src/main/java/com/zimaai/codetrace/storage/TraceJsonMapper.java`
  - 读取 v2 文档；兼容把 v1 `current.nodes` 迁移到 v2 `nodes`。

### Storage and controller

- 修改：`src/main/java/com/zimaai/codetrace/storage/TraceStorageService.java`
  - 保持文件管理能力，但面向 v2 `TraceDocument`。
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java`
  - 只保留当前文件、当前文档、待建链 source id。
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/TraceDocumentEditor.java`
  - 承载纯文档变换：保存 note、增删改节点、建链、取消链接、整对移动、整对删除。
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java`
  - 使用 `TraceDocumentEditor` 执行立即持久化操作，移除录制/dirty/auto-save/refresh decision 逻辑。

### Tool Window

- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
  - 去掉 recording/history/file save；增加两个 note 保存按钮和链接动作。
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java`
  - 节点列表改成 `JBList<TraceNode>`，增加 `Set as Source / Link To Here / Unlink / Save Trace Note / Save Node Note`。
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/TraceFileListPanel.java`
  - 保持文件操作，适配新的控制器刷新路径。
- 删除：`src/main/java/com/zimaai/codetrace/toolwindow/HistoryListPanel.java`
  - 单版本模式不再需要历史面板。
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/LinkedNodeListCellRenderer.java`
  - 仅显示 `displayName`，通过非文本样式区分 source/target/pending-source。
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceProjectService.java`
  - 项目级共享控制器、当前选中文档和 Tool Window 刷新回调。
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceToolWindowFactory.java`
  - 改为从项目级服务获取控制器，不再订阅导航录制监听器。

### Editor popup action

- 创建：`src/main/java/com/zimaai/codetrace/actions/TraceNodeCaptureService.java`
  - 定义从编辑器上下文捕获 source 节点和可选 target 节点的接口。
- 创建：`src/main/java/com/zimaai/codetrace/actions/PsiTraceNodeCaptureService.java`
  - 基于 PSI 和当前 caret 计算 `displayName`、导航元数据和单个候选 target。
- 创建：`src/main/java/com/zimaai/codetrace/actions/TraceUserPrompts.java`
  - 封装“请先选择 JSON”“是否补建 target/link”提示，方便测试。
- 创建：`src/main/java/com/zimaai/codetrace/actions/SwingTraceUserPrompts.java`
  - 真实 Swing 提示实现。
- 创建：`src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceHandler.java`
  - 编排捕获 source、可选 target、追加节点、建链和刷新 UI。
- 创建：`src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceAction.java`
  - 注册到 `EditorPopupMenu` 的 IntelliJ action。
- 修改：`src/main/resources/META-INF/plugin.xml`
  - 注册项目级服务和 editor popup action。

### Tests and docs

- 修改：`src/test/java/com/zimaai/codetrace/storage/TraceJsonMapperTest.java`
  - 覆盖 v1 -> v2 迁移和 v2 round-trip。
- 修改：`src/test/java/com/zimaai/codetrace/storage/TraceStorageServiceTest.java`
  - 更新到 v2 文档结构。
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java`
  - 覆盖立即持久化、节点链接约束、整对移动/删除。
- 创建：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTest.java`
  - 覆盖新按钮、旧按钮移除、node list 只显示 `displayName`。
- 创建：`src/test/java/com/zimaai/codetrace/toolwindow/LinkedNodeListCellRendererTest.java`
  - 覆盖 source/target 的非文本样式差异和文本内容约束。
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/PluginDescriptorTest.java`
  - 校验 action 和项目级服务注册。
- 创建：`src/test/java/com/zimaai/codetrace/actions/AddToCodeTraceHandlerTest.java`
  - 覆盖 source-only 和 source+target+link 两种右键追加流程。
- 删除：`src/test/java/com/zimaai/codetrace/recording/TraceRecordingServiceTest.java`
  - 录制功能被移除。
- 修改：`docs/superpowers/plans/2026-05-29-code-trace-manual-smoke-checklist.md`
  - 替换为单版本、手动链接、右键添加的新冒烟清单。

## Task 1: 升级 trace schema 到 v2 并兼容读取旧 JSON

**文件：**
- 创建：`src/main/java/com/zimaai/codetrace/model/TraceLink.java`
- 创建：`src/main/java/com/zimaai/codetrace/model/TraceLinkKind.java`
- 修改：`src/main/java/com/zimaai/codetrace/model/TraceDocument.java`
- 删除：`src/main/java/com/zimaai/codetrace/model/TraceVersion.java`
- 删除：`src/main/java/com/zimaai/codetrace/model/TraceVersionSource.java`
- 修改：`src/main/java/com/zimaai/codetrace/storage/TraceJsonMapper.java`
- 修改：`src/test/java/com/zimaai/codetrace/storage/TraceJsonMapperTest.java`
- 修改：`src/test/java/com/zimaai/codetrace/storage/TraceStorageServiceTest.java`

- [ ] **步骤 1：编写失败的 mapper 和 storage 测试**

```java
package com.zimaai.codetrace.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TraceJsonMapperTest {
    @Test
    void migratesSchemaOneCurrentNodesIntoSchemaTwoDocument() throws Exception {
        String legacyJson = """
                {
                  "schemaVersion": 1,
                  "id": "trace-auth-login",
                  "name": "Auth Login",
                  "description": "legacy note",
                  "createdAt": "2026-05-28T09:00:00Z",
                  "updatedAt": "2026-05-28T10:00:00Z",
                  "current": {
                    "versionId": "v1",
                    "source": "MANUAL",
                    "recordedAt": "2026-05-28T10:00:00Z",
                    "updatedAt": "2026-05-28T10:00:00Z",
                    "nodeDedupEnabled": true,
                    "nodes": [
                      {
                        "id": "node-1",
                        "displayName": "return authService.login(user);",
                        "qualifiedName": "AuthController#login",
                        "signature": "login(User user)",
                        "filePath": "src/AuthController.java",
                        "line": 21,
                        "language": "JAVA",
                        "note": "legacy",
                        "navigationHint": "AuthController#login(User)"
                      }
                    ]
                  },
                  "history": []
                }
                """;

        TraceDocument restored = new TraceJsonMapper().read(legacyJson);

        assertEquals(2, restored.schemaVersion());
        assertEquals(1, restored.nodes().size());
        assertEquals("return authService.login(user);", restored.nodes().get(0).displayName());
        assertEquals(List.of(), restored.links());
    }

    @Test
    void writesAndReadsSchemaTwoNodesAndLinks() throws Exception {
        TraceNode source = new TraceNode(
                "node-1",
                "return authService.login(user);",
                "AuthController#login",
                "login(User user)",
                "src/AuthController.java",
                21,
                "JAVA",
                "source note",
                "AuthController#login(User)");
        TraceNode target = new TraceNode(
                "node-2",
                "public User login(User user) {",
                "AuthService#login",
                "login(User user)",
                "src/AuthService.java",
                14,
                "JAVA",
                "target note",
                "AuthService#login(User)");
        TraceDocument document = new TraceDocument(
                2,
                "trace-auth-login",
                "Auth Login",
                "trace note",
                Instant.parse("2026-05-29T09:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(source, target),
                List.of(new TraceLink(
                        "link-1",
                        "node-1",
                        "node-2",
                        Instant.parse("2026-05-29T10:00:00Z"),
                        TraceLinkKind.DETECTED)));

        TraceJsonMapper mapper = new TraceJsonMapper();
        String json = mapper.write(document);
        TraceDocument restored = mapper.read(json);

        assertTrue(json.contains("\"schemaVersion\" : 2"));
        assertEquals(2, restored.nodes().size());
        assertEquals(1, restored.links().size());
        assertEquals(TraceLinkKind.DETECTED, restored.links().get(0).kind());
    }
}
```

```java
package com.zimaai.codetrace.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
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
    void createsLoadsCopiesRenamesAndDeletesSchemaTwoTraceFiles() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        TraceNode node = new TraceNode(
                "node-1",
                "return authService.login(user);",
                "AuthController#login",
                "login(User user)",
                "src/AuthController.java",
                21,
                "JAVA",
                "",
                "AuthController#login(User)");
        TraceDocument document = new TraceDocument(
                2,
                "trace-1",
                "Trace 1",
                "note",
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(node),
                List.of(new TraceLink(
                        "link-1",
                        "node-1",
                        "node-1-target",
                        Instant.parse("2026-05-29T10:01:00Z"),
                        TraceLinkKind.MANUAL)));

        storage.save("trace-1.json", document);
        assertTrue(Files.exists(tempDir.resolve("code-trace").resolve("trace-1.json")));
        assertEquals(List.of("trace-1.json"), storage.listFiles());
        assertEquals(1, storage.load("trace-1.json").nodes().size());

        storage.copy("trace-1.json", "trace-1-copy.json");
        storage.rename("trace-1-copy.json", "trace-1-renamed.json");
        storage.delete("trace-1-renamed.json");

        assertEquals(List.of("trace-1.json"), storage.listFiles());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.storage.TraceJsonMapperTest --tests com.zimaai.codetrace.storage.TraceStorageServiceTest`

预期：FAIL，编译错误会指出 `TraceLink`、`TraceLinkKind`、`TraceDocument.nodes()`、`TraceDocument.links()` 不存在，`TraceVersion` 相关构造也不再匹配。

- [ ] **步骤 3：实现 v2 模型和 v1 迁移**

`src/main/java/com/zimaai/codetrace/model/TraceLinkKind.java`：

```java
package com.zimaai.codetrace.model;

public enum TraceLinkKind {
    MANUAL,
    DETECTED
}
```

`src/main/java/com/zimaai/codetrace/model/TraceLink.java`：

```java
package com.zimaai.codetrace.model;

import java.time.Instant;

public record TraceLink(
        String id,
        String sourceNodeId,
        String targetNodeId,
        Instant createdAt,
        TraceLinkKind kind) {
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
        List<TraceNode> nodes,
        List<TraceLink> links) {
}
```

`src/main/java/com/zimaai/codetrace/storage/TraceJsonMapper.java`：

```java
package com.zimaai.codetrace.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class TraceJsonMapper {
    private final ObjectMapper mapper;

    public TraceJsonMapper() {
        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public String write(TraceDocument document) throws Exception {
        return mapper.writeValueAsString(document);
    }

    public TraceDocument read(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        int schemaVersion = root.path("schemaVersion").asInt(1);
        if (schemaVersion >= 2) {
            return mapper.treeToValue(root, TraceDocument.class);
        }
        return migrateSchemaOne(root);
    }

    private TraceDocument migrateSchemaOne(JsonNode root) throws Exception {
        List<TraceNode> migratedNodes = new ArrayList<>();
        JsonNode currentNodes = root.path("current").path("nodes");
        if (currentNodes.isArray()) {
            for (JsonNode node : currentNodes) {
                migratedNodes.add(mapper.treeToValue(node, TraceNode.class));
            }
        }
        return new TraceDocument(
                2,
                root.path("id").asText(),
                root.path("name").asText(),
                root.path("description").asText(""),
                mapper.convertValue(root.path("createdAt"), Instant.class),
                mapper.convertValue(root.path("updatedAt"), Instant.class),
                List.copyOf(migratedNodes),
                List.<TraceLink>of());
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.storage.TraceJsonMapperTest --tests com.zimaai.codetrace.storage.TraceStorageServiceTest`

预期：PASS，v1 JSON 会被读成 v2 文档，v2 round-trip 和文件管理测试全部通过。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/model/TraceDocument.java src/main/java/com/zimaai/codetrace/model/TraceNode.java src/main/java/com/zimaai/codetrace/model/TraceLink.java src/main/java/com/zimaai/codetrace/model/TraceLinkKind.java src/main/java/com/zimaai/codetrace/storage/TraceJsonMapper.java src/test/java/com/zimaai/codetrace/storage/TraceJsonMapperTest.java src/test/java/com/zimaai/codetrace/storage/TraceStorageServiceTest.java
git rm src/main/java/com/zimaai/codetrace/model/TraceVersion.java src/main/java/com/zimaai/codetrace/model/TraceVersionSource.java
git commit -m "feat: migrate trace schema to single-version links"
```

## Task 2: 用立即持久化的控制器替换 recording/history/save 流程

**文件：**
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/TraceDocumentEditor.java`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java`
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java`

- [ ] **步骤 1：编写失败的控制器测试**

```java
package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.storage.TraceJsonMapper;
import com.zimaai.codetrace.storage.TraceStorageService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeTraceControllerTest {
    @TempDir
    Path tempDir;

    @Test
    void savesTraceAndNodeNotesDirectlyToDisk() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-1.json", documentWithTwoNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);

        controller.load("trace-1.json");
        controller.saveDescription("saved trace note");
        controller.saveNodeNote("node-1", "saved node note");

        TraceDocument reloaded = storage.load("trace-1.json");
        assertEquals("saved trace note", reloaded.description());
        assertEquals("saved node note", reloaded.nodes().get(0).note());
    }

    @Test
    void linksMoveAndDeleteOperateOnWholePair() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-2.json", documentWithTwoNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);

        controller.load("trace-2.json");
        controller.setPendingLinkSource("node-1");
        controller.linkPendingSourceTo("node-2", TraceLinkKind.MANUAL);

        assertEquals(1, controller.state().currentDocument().links().size());

        int movedTo = controller.moveNodeOrPair("node-2", 1);
        assertEquals(2, movedTo);
        List<String> order = controller.state().currentDocument().nodes().stream()
                .map(TraceNode::id)
                .collect(Collectors.toList());
        assertEquals(List.of("node-3", "node-1", "node-2"), order);

        controller.deleteNodeOrPair("node-1");
        assertEquals(List.of("node-3"), controller.state().currentDocument().nodes().stream()
                .map(TraceNode::id)
                .collect(Collectors.toList()));
        assertTrue(controller.state().currentDocument().links().isEmpty());
    }

    @Test
    void rejectsSecondLinkForAlreadyLinkedNode() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-3.json", documentWithThreeNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);

        controller.load("trace-3.json");
        controller.setPendingLinkSource("node-1");
        controller.linkPendingSourceTo("node-2", TraceLinkKind.MANUAL);

        controller.setPendingLinkSource("node-1");
        IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> controller.linkPendingSourceTo("node-3", TraceLinkKind.MANUAL));

        assertEquals("Each node can participate in at most one link", exception.getMessage());
    }

    private static TraceDocument documentWithTwoNodes() {
        return new TraceDocument(
                2,
                "trace-1",
                "Trace 1",
                "note",
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(
                        new TraceNode("node-1", "line 1", "A#a", "a()", "A.java", 10, "JAVA", "", "A#a"),
                        new TraceNode("node-2", "line 2", "B#b", "b()", "B.java", 20, "JAVA", "", "B#b"),
                        new TraceNode("node-3", "line 3", "C#c", "c()", "C.java", 30, "JAVA", "", "C#c")),
                List.<TraceLink>of());
    }

    private static TraceDocument documentWithThreeNodes() {
        return documentWithTwoNodes();
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.CodeTraceControllerTest`

预期：FAIL，编译错误会指出 `saveDescription`、`saveNodeNote(String, ...)`、`setPendingLinkSource`、`linkPendingSourceTo`、`moveNodeOrPair`、`deleteNodeOrPair` 不存在，旧的 recording 构造参数也不匹配。

- [ ] **步骤 3：实现纯文档编辑器和立即持久化控制器**

`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java`：

```java
package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;

public final class CodeTraceState {
    private String currentFileName;
    private TraceDocument currentDocument;
    private String pendingLinkSourceId;

    public String currentFileName() {
        return currentFileName;
    }

    public TraceDocument currentDocument() {
        return currentDocument;
    }

    public String pendingLinkSourceId() {
        return pendingLinkSourceId;
    }

    void load(String fileName, TraceDocument document) {
        this.currentFileName = fileName;
        this.currentDocument = document;
        this.pendingLinkSourceId = null;
    }

    void replaceDocument(TraceDocument document) {
        this.currentDocument = document;
    }

    void setPendingLinkSourceId(String pendingLinkSourceId) {
        this.pendingLinkSourceId = pendingLinkSourceId;
    }

    void clearPendingLinkSource() {
        this.pendingLinkSourceId = null;
    }
}
```

`src/main/java/com/zimaai/codetrace/toolwindow/TraceDocumentEditor.java`：

```java
package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TraceDocumentEditor {
    public TraceDocument saveDescription(TraceDocument document, String description, Instant now) {
        return new TraceDocument(
                2,
                document.id(),
                document.name(),
                description,
                document.createdAt(),
                now,
                document.nodes(),
                document.links());
    }

    public TraceDocument saveNodeNote(TraceDocument document, String nodeId, String note, Instant now) {
        List<TraceNode> updatedNodes = new ArrayList<>();
        for (TraceNode node : document.nodes()) {
            if (node.id().equals(nodeId)) {
                updatedNodes.add(new TraceNode(
                        node.id(),
                        node.displayName(),
                        node.qualifiedName(),
                        node.signature(),
                        node.filePath(),
                        node.line(),
                        node.language(),
                        note,
                        node.navigationHint()));
            } else {
                updatedNodes.add(node);
            }
        }
        return new TraceDocument(
                2,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                List.copyOf(updatedNodes),
                document.links());
    }

    public TraceDocument addNode(TraceDocument document, TraceNode node, Instant now) {
        List<TraceNode> nodes = new ArrayList<>(document.nodes());
        nodes.add(node);
        return new TraceDocument(
                2,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                List.copyOf(nodes),
                document.links());
    }

    public TraceDocument updateNode(TraceDocument document, TraceNode replacement, Instant now) {
        List<TraceNode> nodes = new ArrayList<>(document.nodes());
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).id().equals(replacement.id())) {
                nodes.set(i, replacement);
                break;
            }
        }
        return new TraceDocument(
                2,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                List.copyOf(nodes),
                document.links());
    }

    public TraceDocument link(TraceDocument document, String sourceNodeId, String targetNodeId, TraceLinkKind kind, Instant now) {
        if (sourceNodeId.equals(targetNodeId)) {
            throw new IllegalArgumentException("Cannot link a node to itself");
        }
        for (TraceLink link : document.links()) {
            if (link.sourceNodeId().equals(sourceNodeId)
                    || link.targetNodeId().equals(sourceNodeId)
                    || link.sourceNodeId().equals(targetNodeId)
                    || link.targetNodeId().equals(targetNodeId)) {
                throw new IllegalArgumentException("Each node can participate in at most one link");
            }
        }
        List<TraceLink> links = new ArrayList<>(document.links());
        links.add(new TraceLink("link-" + UUID.randomUUID(), sourceNodeId, targetNodeId, now, kind));
        return new TraceDocument(
                2,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                document.nodes(),
                List.copyOf(links));
    }

    public TraceDocument unlink(TraceDocument document, String nodeId, Instant now) {
        List<TraceLink> links = document.links().stream()
                .filter(link -> !link.sourceNodeId().equals(nodeId) && !link.targetNodeId().equals(nodeId))
                .toList();
        return new TraceDocument(
                2,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                document.nodes(),
                links);
    }
}
```

`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java`：

```java
package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.storage.TraceStorageService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public final class CodeTraceController {
    private final TraceStorageService storage;
    private final Function<TraceNode, Boolean> navigationHandler;
    private final TraceDocumentEditor editor = new TraceDocumentEditor();
    private final CodeTraceState state = new CodeTraceState();

    public CodeTraceController(TraceStorageService storage, Function<TraceNode, Boolean> navigationHandler) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.navigationHandler = Objects.requireNonNull(navigationHandler, "navigationHandler");
    }

    public List<String> loadFileNames() {
        return storage.listFiles();
    }

    public TraceDocument load(String fileName) {
        TraceDocument document = storage.load(fileName);
        state.load(fileName, document);
        return document;
    }

    public void refreshCurrentFile() {
        if (state.currentFileName() != null) {
            state.load(state.currentFileName(), storage.load(state.currentFileName()));
        }
    }

    public void saveDescription(String description) {
        persist(editor.saveDescription(requireDocument(), description, Instant.now()));
    }

    public void saveNodeNote(String nodeId, String note) {
        persist(editor.saveNodeNote(requireDocument(), nodeId, note, Instant.now()));
    }

    public int addNode(TraceNode node) {
        TraceDocument updated = editor.addNode(requireDocument(), node, Instant.now());
        persist(updated);
        return updated.nodes().size() - 1;
    }

    public int addOrReuseNode(TraceNode candidate) {
        List<TraceNode> nodes = requireDocument().nodes();
        for (int i = 0; i < nodes.size(); i++) {
            TraceNode existing = nodes.get(i);
            if (existing.displayName().equals(candidate.displayName())
                    && existing.filePath().equals(candidate.filePath())
                    && existing.line() == candidate.line()) {
                return i;
            }
        }
        return addNode(new TraceNode(
                "node-" + UUID.randomUUID(),
                candidate.displayName(),
                candidate.qualifiedName(),
                candidate.signature(),
                candidate.filePath(),
                candidate.line(),
                candidate.language(),
                candidate.note(),
                candidate.navigationHint()));
    }

    public void updateNode(TraceNode node) {
        persist(editor.updateNode(requireDocument(), node, Instant.now()));
    }

    public void setPendingLinkSource(String nodeId) {
        state.setPendingLinkSourceId(nodeId);
    }

    public void linkPendingSourceTo(String targetNodeId, TraceLinkKind kind) {
        String sourceNodeId = Objects.requireNonNull(state.pendingLinkSourceId(), "pendingLinkSourceId");
        persist(editor.link(requireDocument(), sourceNodeId, targetNodeId, kind, Instant.now()));
        state.clearPendingLinkSource();
    }

    public void unlinkNode(String nodeId) {
        persist(editor.unlink(requireDocument(), nodeId, Instant.now()));
        if (nodeId.equals(state.pendingLinkSourceId())) {
            state.clearPendingLinkSource();
        }
    }

    public int moveNodeOrPair(String nodeId, int offset) {
        TraceDocument updated = moveInternal(requireDocument(), nodeId, offset, Instant.now());
        persist(updated);
        return indexOfNode(updated.nodes(), nodeId);
    }

    public void deleteNodeOrPair(String nodeId) {
        persist(deleteInternal(requireDocument(), nodeId, Instant.now()));
        if (nodeId.equals(state.pendingLinkSourceId())) {
            state.clearPendingLinkSource();
        }
    }

    public boolean navigateToNode(TraceNode node) {
        return navigationHandler.apply(node);
    }

    public TraceDocument createNewFile(String fileName, String displayName) {
        Instant now = Instant.now();
        TraceDocument document = new TraceDocument(
                2,
                "trace-" + UUID.randomUUID(),
                displayName,
                "",
                now,
                now,
                List.of(),
                List.of());
        storage.save(fileName, document);
        state.load(fileName, document);
        return document;
    }

    public TraceDocument ensureAnyFileLoaded() {
        List<String> files = storage.listFiles();
        if (!files.isEmpty()) {
            return load(files.get(0));
        }
        return createNewFile("trace-" + Instant.now().getEpochSecond() + ".json", "New Trace");
    }

    public CodeTraceState state() {
        return state;
    }

    private void persist(TraceDocument document) {
        storage.save(state.currentFileName(), document);
        state.replaceDocument(storage.load(state.currentFileName()));
    }

    private TraceDocument requireDocument() {
        return Objects.requireNonNull(state.currentDocument(), "currentDocument");
    }

    private static int indexOfNode(List<TraceNode> nodes, String nodeId) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).id().equals(nodeId)) {
                return i;
            }
        }
        return -1;
    }

    private static TraceDocument moveInternal(TraceDocument document, String nodeId, int offset, Instant now) {
        List<TraceNode> nodes = new ArrayList<>(document.nodes());
        List<String> affectedIds = linkedNodeIds(document.links(), nodeId);
        List<Integer> sourceIndexes = new ArrayList<>();
        for (String affectedId : affectedIds) {
            sourceIndexes.add(indexOfNode(nodes, affectedId));
        }
        List<Integer> targetIndexes = sourceIndexes.stream().map(index -> index + offset).toList();
        for (Integer targetIndex : targetIndexes) {
            if (targetIndex < 0 || targetIndex >= nodes.size()) {
                return document;
            }
        }
        List<TraceNode> original = new ArrayList<>(nodes);
        for (int i = 0; i < sourceIndexes.size(); i++) {
            nodes.set(targetIndexes.get(i), original.get(sourceIndexes.get(i)));
        }
        for (int i = 0; i < sourceIndexes.size(); i++) {
            if (!targetIndexes.contains(sourceIndexes.get(i))) {
                nodes.set(sourceIndexes.get(i), original.get(targetIndexes.get(i)));
            }
        }
        return new TraceDocument(2, document.id(), document.name(), document.description(), document.createdAt(), now, List.copyOf(nodes), document.links());
    }

    private static TraceDocument deleteInternal(TraceDocument document, String nodeId, Instant now) {
        List<String> affectedIds = linkedNodeIds(document.links(), nodeId);
        List<TraceNode> nodes = document.nodes().stream()
                .filter(node -> !affectedIds.contains(node.id()))
                .toList();
        List<TraceLink> links = document.links().stream()
                .filter(link -> !affectedIds.contains(link.sourceNodeId()) && !affectedIds.contains(link.targetNodeId()))
                .toList();
        return new TraceDocument(2, document.id(), document.name(), document.description(), document.createdAt(), now, nodes, links);
    }

    private static List<String> linkedNodeIds(List<TraceLink> links, String nodeId) {
        for (TraceLink link : links) {
            if (link.sourceNodeId().equals(nodeId)) {
                return List.of(link.sourceNodeId(), link.targetNodeId());
            }
            if (link.targetNodeId().equals(nodeId)) {
                return List.of(link.sourceNodeId(), link.targetNodeId());
            }
        }
        return List.of(nodeId);
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.CodeTraceControllerTest`

预期：PASS，note 保存直接落盘，建链约束生效，linked pair 的移动和删除行为符合设计。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/toolwindow/TraceDocumentEditor.java src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java
git commit -m "feat: switch controller to immediate single-version persistence"
```

## Task 3: 重建 Tool Window 为单版本节点编辑器

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/TraceFileListPanel.java`
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/LinkedNodeListCellRenderer.java`
- 删除：`src/main/java/com/zimaai/codetrace/toolwindow/HistoryListPanel.java`
- 创建：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTest.java`
- 创建：`src/test/java/com/zimaai/codetrace/toolwindow/LinkedNodeListCellRendererTest.java`

- [ ] **步骤 1：编写失败的 UI 单元测试**

```java
package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.storage.TraceJsonMapper;
import com.zimaai.codetrace.storage.TraceStorageService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeTracePanelTest {
    @TempDir
    Path tempDir;

    @Test
    void rendersNewNoteAndLinkActionsWithoutRecordingOrHistoryButtons() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        TraceDocument document = new TraceDocument(
                2,
                "trace-1",
                "Trace 1",
                "trace note",
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(new TraceNode("node-1", "return authService.login(user);", "A#a", "a()", "A.java", 1, "JAVA", "", "A#a")),
                List.of());
        storage.save("trace-1.json", document);
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        CodeTracePanel panel = new CodeTracePanel(controller);

        panel.reloadFromDisk();

        assertNotNull(panel.findButton("Refresh"));
        assertNotNull(panel.findButton("Save Trace Note"));
        assertNotNull(panel.findButton("Save Node Note"));
        assertNotNull(panel.findButton("Set as Source"));
        assertNotNull(panel.findButton("Link To Here"));
        assertNotNull(panel.findButton("Unlink"));
        assertNull(panel.findButton("Start Recording"));
        assertNull(panel.findButton("Stop Recording"));
        assertNull(panel.findButton("Save"));
        assertNull(panel.findButton("Add Node"));
    }
}
```

```java
package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.Component;
import java.time.Instant;
import java.util.List;
import javax.swing.JList;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class LinkedNodeListCellRendererTest {
    @Test
    void keepsRowTextEqualToDisplayNameAndUsesDifferentDecorationForRoles() {
        TraceNode source = new TraceNode("node-1", "source line", "A#a", "a()", "A.java", 1, "JAVA", "", "A#a");
        TraceNode target = new TraceNode("node-2", "target line", "B#b", "b()", "B.java", 2, "JAVA", "", "B#b");
        TraceDocument document = new TraceDocument(
                2,
                "trace-1",
                "Trace 1",
                "",
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(source, target),
                List.of(new TraceLink("link-1", "node-1", "node-2", Instant.parse("2026-05-29T10:01:00Z"), TraceLinkKind.MANUAL)));

        LinkedNodeListCellRenderer renderer = new LinkedNodeListCellRenderer(() -> document, () -> null);
        JList<TraceNode> list = new JList<>(new TraceNode[]{source, target});

        Component sourceComponent = renderer.getListCellRendererComponent(list, source, 0, false, false);
        Component targetComponent = renderer.getListCellRendererComponent(list, target, 1, false, false);

        assertEquals("source line", ((JLabel) sourceComponent).getText());
        assertEquals("target line", ((JLabel) targetComponent).getText());
        assertNotEquals(sourceComponent.getBackground(), targetComponent.getBackground());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.CodeTracePanelTest --tests com.zimaai.codetrace.toolwindow.LinkedNodeListCellRendererTest`

预期：FAIL，编译错误会指出 `CodeTracePanel(CodeTraceController)` 构造不存在、`Save Trace Note` 等按钮不存在、列表仍是 `String` 而不是 `TraceNode`。

- [ ] **步骤 3：实现单版本 UI 和 node renderer**

`src/main/java/com/zimaai/codetrace/toolwindow/LinkedNodeListCellRenderer.java`：

```java
package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.Color;
import java.awt.Component;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.JLabel;

public final class LinkedNodeListCellRenderer extends DefaultListCellRenderer {
    private final Supplier<TraceDocument> documentSupplier;
    private final Supplier<String> pendingSourceSupplier;

    public LinkedNodeListCellRenderer(Supplier<TraceDocument> documentSupplier, Supplier<String> pendingSourceSupplier) {
        this.documentSupplier = documentSupplier;
        this.pendingSourceSupplier = pendingSourceSupplier;
    }

    @Override
    public Component getListCellRendererComponent(
            JList<?> list,
            Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus) {
        JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        TraceNode node = (TraceNode) value;
        label.setText(node.displayName());
        TraceDocument document = documentSupplier.get();
        String pendingSourceId = pendingSourceSupplier.get();
        if (node.id().equals(pendingSourceId)) {
            label.setBackground(new Color(255, 244, 214));
        } else if (document != null) {
            for (TraceLink link : document.links()) {
                if (Objects.equals(link.sourceNodeId(), node.id())) {
                    label.setBackground(new Color(222, 242, 255));
                }
                if (Objects.equals(link.targetNodeId(), node.id())) {
                    label.setBackground(new Color(228, 255, 230));
                }
            }
        }
        return label;
    }
}
```

`src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java`：

```java
package com.zimaai.codetrace.toolwindow;

import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.BorderLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

public final class TraceEditorPanel {
    private final JBTextArea traceNote = new JBTextArea();
    private final JButton saveTraceNoteButton = new JButton("Save Trace Note");
    private final JBList<TraceNode> nodeList = new JBList<>();
    private final JBTextArea nodeNote = new JBTextArea();
    private final JButton saveNodeNoteButton = new JButton("Save Node Note");
    private final JButton editNodeButton = new JButton("Edit Node");
    private final JButton deleteNodeButton = new JButton("Delete Node");
    private final JButton moveUpButton = new JButton("Move Up");
    private final JButton moveDownButton = new JButton("Move Down");
    private final JButton setAsSourceButton = new JButton("Set as Source");
    private final JButton linkToHereButton = new JButton("Link To Here");
    private final JButton unlinkButton = new JButton("Unlink");
    private final JLabel linkStatus = new JLabel("Link source: none");
    private final JPanel root = new JPanel(new BorderLayout());

    public TraceEditorPanel() {
        JPanel traceNotePanel = new JPanel(new BorderLayout());
        traceNotePanel.add(new JBScrollPane(traceNote), BorderLayout.CENTER);
        traceNotePanel.add(saveTraceNoteButton, BorderLayout.SOUTH);

        JPanel nodeToolbar = new JPanel();
        nodeToolbar.add(editNodeButton);
        nodeToolbar.add(deleteNodeButton);
        nodeToolbar.add(moveUpButton);
        nodeToolbar.add(moveDownButton);
        nodeToolbar.add(setAsSourceButton);
        nodeToolbar.add(linkToHereButton);
        nodeToolbar.add(unlinkButton);

        JPanel nodeNotePanel = new JPanel(new BorderLayout());
        nodeNotePanel.add(new JBScrollPane(nodeNote), BorderLayout.CENTER);
        nodeNotePanel.add(saveNodeNoteButton, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JBScrollPane(nodeList),
                nodeNotePanel);
        split.setResizeWeight(0.7d);

        JPanel content = new JPanel(new BorderLayout());
        content.add(nodeToolbar, BorderLayout.NORTH);
        content.add(linkStatus, BorderLayout.SOUTH);
        content.add(split, BorderLayout.CENTER);

        root.add(traceNotePanel, BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
    }

    public JComponent component() { return root; }
    public JBTextArea traceNote() { return traceNote; }
    public JButton saveTraceNoteButton() { return saveTraceNoteButton; }
    public JBList<TraceNode> nodeList() { return nodeList; }
    public JBTextArea nodeNote() { return nodeNote; }
    public JButton saveNodeNoteButton() { return saveNodeNoteButton; }
    public JButton editNodeButton() { return editNodeButton; }
    public JButton deleteNodeButton() { return deleteNodeButton; }
    public JButton moveUpButton() { return moveUpButton; }
    public JButton moveDownButton() { return moveDownButton; }
    public JButton setAsSourceButton() { return setAsSourceButton; }
    public JButton linkToHereButton() { return linkToHereButton; }
    public JButton unlinkButton() { return unlinkButton; }
    public JLabel linkStatus() { return linkStatus; }
}
```

`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`：

```java
package com.zimaai.codetrace.toolwindow;

import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBList;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.BorderLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class CodeTracePanel {
    private final CodeTraceController controller;
    private final JPanel root = new JPanel(new BorderLayout());
    private final Map<String, JButton> buttons = new HashMap<>();
    private final TraceFileListPanel fileListPanel = new TraceFileListPanel();
    private final TraceEditorPanel editorPanel = new TraceEditorPanel();
    private boolean syncingTraceNote;
    private boolean syncingNodeNote;
    private String selectedNodeId;

    public CodeTracePanel(CodeTraceController controller) {
        this.controller = controller;
        editorPanel.nodeList().setCellRenderer(
                new LinkedNodeListCellRenderer(() -> controller.state().currentDocument(), () -> controller.state().pendingLinkSourceId()));
        configureLayout();
        configureSelection();
        configureNoteButtons();
        configureNodeActions();
    }

    public JComponent getComponent() {
        return root;
    }

    public JButton findButton(String text) {
        return buttons.get(text);
    }

    public void reloadFromDisk() {
        controller.ensureAnyFileLoaded();
        rebuildView();
    }

    private void configureLayout() {
        JPanel toolbar = new JPanel();
        addButton(toolbar, "Refresh", this::refresh);
        addButton(toolbar, "Save Trace Note", () -> controller.saveDescription(editorPanel.traceNote().getText()));
        addButton(toolbar, "Save Node Note", this::saveSelectedNodeNote);
        addButton(toolbar, "Set as Source", this::setSelectedAsSource);
        addButton(toolbar, "Link To Here", this::linkToSelectedNode);
        addButton(toolbar, "Unlink", this::unlinkSelectedNode);

        JBSplitter split = new JBSplitter(false, 0.25f);
        split.setFirstComponent(fileListPanel.component());
        split.setSecondComponent(editorPanel.component());

        root.add(toolbar, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
    }

    private void configureSelection() {
        fileListPanel.list().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && fileListPanel.list().getSelectedValue() != null) {
                controller.load(fileListPanel.list().getSelectedValue());
                rebuildView();
            }
        });
        editorPanel.nodeList().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                TraceNode node = editorPanel.nodeList().getSelectedValue();
                selectedNodeId = node == null ? null : node.id();
                syncSelectedNodeNote();
            }
        });
        editorPanel.nodeList().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2 && editorPanel.nodeList().getSelectedValue() != null) {
                    controller.navigateToNode(editorPanel.nodeList().getSelectedValue());
                }
            }
        });
    }

    private void configureNoteButtons() {
        editorPanel.traceNote().getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refreshTraceNoteButton(); }
            @Override public void removeUpdate(DocumentEvent e) { refreshTraceNoteButton(); }
            @Override public void changedUpdate(DocumentEvent e) { refreshTraceNoteButton(); }
        });
        editorPanel.nodeNote().getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refreshNodeNoteButton(); }
            @Override public void removeUpdate(DocumentEvent e) { refreshNodeNoteButton(); }
            @Override public void changedUpdate(DocumentEvent e) { refreshNodeNoteButton(); }
        });
    }

    private void configureNodeActions() {
        editorPanel.deleteNodeButton().addActionListener(event -> {
            if (selectedNodeId != null) {
                controller.deleteNodeOrPair(selectedNodeId);
                rebuildView();
            }
        });
        editorPanel.moveUpButton().addActionListener(event -> moveSelected(-1));
        editorPanel.moveDownButton().addActionListener(event -> moveSelected(1));
        editorPanel.setAsSourceButton().addActionListener(event -> setSelectedAsSource());
        editorPanel.linkToHereButton().addActionListener(event -> linkToSelectedNode());
        editorPanel.unlinkButton().addActionListener(event -> unlinkSelectedNode());
    }

    private void refresh() {
        controller.refreshCurrentFile();
        rebuildView();
    }

    private void saveSelectedNodeNote() {
        if (selectedNodeId != null) {
            controller.saveNodeNote(selectedNodeId, editorPanel.nodeNote().getText());
            rebuildView();
        }
    }

    private void setSelectedAsSource() {
        if (selectedNodeId != null) {
            controller.setPendingLinkSource(selectedNodeId);
            rebuildView();
        }
    }

    private void linkToSelectedNode() {
        if (selectedNodeId != null && controller.state().pendingLinkSourceId() != null) {
            controller.linkPendingSourceTo(selectedNodeId, com.zimaai.codetrace.model.TraceLinkKind.MANUAL);
            rebuildView();
        }
    }

    private void unlinkSelectedNode() {
        if (selectedNodeId != null) {
            controller.unlinkNode(selectedNodeId);
            rebuildView();
        }
    }

    private void moveSelected(int offset) {
        if (selectedNodeId != null) {
            controller.moveNodeOrPair(selectedNodeId, offset);
            rebuildView();
        }
    }

    private void rebuildView() {
        List<String> files = controller.loadFileNames();
        fileListPanel.list().setListData(files.toArray(String[]::new));
        if (controller.state().currentFileName() != null) {
            fileListPanel.list().setSelectedValue(controller.state().currentFileName(), true);
        }
        if (controller.state().currentDocument() == null) {
            editorPanel.nodeList().setListData(new TraceNode[0]);
            return;
        }
        syncingTraceNote = true;
        editorPanel.traceNote().setText(controller.state().currentDocument().description());
        syncingTraceNote = false;
        editorPanel.nodeList().setListData(controller.state().currentDocument().nodes().toArray(TraceNode[]::new));
        editorPanel.linkStatus().setText("Link source: "
                + (controller.state().pendingLinkSourceId() == null ? "none" : controller.state().pendingLinkSourceId()));
        refreshTraceNoteButton();
        syncSelectedNodeNote();
    }

    private void syncSelectedNodeNote() {
        TraceNode node = editorPanel.nodeList().getSelectedValue();
        syncingNodeNote = true;
        editorPanel.nodeNote().setText(node == null ? "" : node.note());
        syncingNodeNote = false;
        refreshNodeNoteButton();
    }

    private void refreshTraceNoteButton() {
        if (syncingTraceNote || controller.state().currentDocument() == null) {
            editorPanel.saveTraceNoteButton().setEnabled(false);
            return;
        }
        editorPanel.saveTraceNoteButton().setEnabled(
                !editorPanel.traceNote().getText().equals(controller.state().currentDocument().description()));
    }

    private void refreshNodeNoteButton() {
        if (syncingNodeNote) {
            editorPanel.saveNodeNoteButton().setEnabled(false);
            return;
        }
        TraceNode node = editorPanel.nodeList().getSelectedValue();
        editorPanel.saveNodeNoteButton().setEnabled(node != null && !editorPanel.nodeNote().getText().equals(node.note()));
    }

    private void addButton(JPanel toolbar, String label, Runnable action) {
        JButton button = new JButton(label);
        button.addActionListener(event -> action.run());
        buttons.put(label, button);
        toolbar.add(button);
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.CodeTracePanelTest --tests com.zimaai.codetrace.toolwindow.LinkedNodeListCellRendererTest`

预期：PASS，新 UI 按钮存在、旧按钮缺失，列表文本始终等于 `displayName`，source/target 通过非文本样式区分。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java src/main/java/com/zimaai/codetrace/toolwindow/TraceFileListPanel.java src/main/java/com/zimaai/codetrace/toolwindow/LinkedNodeListCellRenderer.java src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTest.java src/test/java/com/zimaai/codetrace/toolwindow/LinkedNodeListCellRendererTest.java
git rm src/main/java/com/zimaai/codetrace/toolwindow/HistoryListPanel.java
git commit -m "feat: rebuild tool window for single-version node linking"
```

## Task 4: 增加项目级服务和编辑器右键“Add to code-trace”动作

**文件：**
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceProjectService.java`
- 创建：`src/main/java/com/zimaai/codetrace/actions/TraceNodeCaptureService.java`
- 创建：`src/main/java/com/zimaai/codetrace/actions/PsiTraceNodeCaptureService.java`
- 创建：`src/main/java/com/zimaai/codetrace/actions/TraceUserPrompts.java`
- 创建：`src/main/java/com/zimaai/codetrace/actions/SwingTraceUserPrompts.java`
- 创建：`src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceHandler.java`
- 创建：`src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceAction.java`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceToolWindowFactory.java`
- 修改：`src/main/resources/META-INF/plugin.xml`
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/PluginDescriptorTest.java`
- 创建：`src/test/java/com/zimaai/codetrace/actions/AddToCodeTraceHandlerTest.java`

- [ ] **步骤 1：编写失败的 action 注册和 handler 测试**

```java
package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PluginDescriptorTest {
    @Test
    void pluginXmlRegistersToolWindowProjectServiceAndEditorPopupAction() throws IOException {
        try (var stream = getClass().getResourceAsStream("/META-INF/plugin.xml")) {
            assertTrue(stream != null, "plugin.xml should exist");
            String xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("factoryClass=\"com.zimaai.codetrace.toolwindow.CodeTraceToolWindowFactory\""));
            assertTrue(xml.contains("serviceImplementation=\"com.zimaai.codetrace.toolwindow.CodeTraceProjectService\""));
            assertTrue(xml.contains("class=\"com.zimaai.codetrace.actions.AddToCodeTraceAction\""));
            assertTrue(xml.contains("group-id=\"EditorPopupMenu\""));
        }
    }
}
```

```java
package com.zimaai.codetrace.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.storage.TraceJsonMapper;
import com.zimaai.codetrace.storage.TraceStorageService;
import com.zimaai.codetrace.toolwindow.CodeTraceController;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AddToCodeTraceHandlerTest {
    @TempDir
    Path tempDir;

    @Test
    void appendsSourceNodeAndDetectedTargetWhenUserAcceptsLink() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.createNewFile("trace-1.json", "Trace 1");

        TraceNode source = new TraceNode(
                "ignored-source-id",
                "return authService.login(user);",
                "AuthController#login",
                "login(User user)",
                "src/AuthController.java",
                21,
                "JAVA",
                "",
                "AuthController#login(User)");
        TraceNode target = new TraceNode(
                "ignored-target-id",
                "public User login(User user) {",
                "AuthService#login",
                "login(User user)",
                "src/AuthService.java",
                14,
                "JAVA",
                "",
                "AuthService#login(User)");

        AtomicBoolean refreshed = new AtomicBoolean(false);
        RecordingPrompts prompts = new RecordingPrompts(true);
        AddToCodeTraceHandler handler = new AddToCodeTraceHandler(
                controller,
                new FakeCaptureService(source, Optional.of(target)),
                prompts,
                () -> refreshed.set(true));

        handler.handle(null, null, null);

        TraceDocument document = controller.state().currentDocument();
        assertEquals(2, document.nodes().size());
        assertEquals(1, document.links().size());
        assertEquals(TraceLinkKind.DETECTED, document.links().get(0).kind());
        assertTrue(refreshed.get());
        assertTrue(prompts.confirmCalled);
    }

    private static final class FakeCaptureService implements TraceNodeCaptureService {
        private final TraceNode source;
        private final Optional<TraceNode> target;

        private FakeCaptureService(TraceNode source, Optional<TraceNode> target) {
            this.source = source;
            this.target = target;
        }

        @Override
        public TraceNode captureCurrentLine(com.intellij.openapi.project.Project project, com.intellij.openapi.editor.Editor editor, com.intellij.psi.PsiFile psiFile) {
            return source;
        }

        @Override
        public Optional<TraceNode> detectTarget(com.intellij.openapi.project.Project project, com.intellij.openapi.editor.Editor editor, com.intellij.psi.PsiFile psiFile) {
            return target;
        }
    }

    private static final class RecordingPrompts implements TraceUserPrompts {
        private final boolean confirm;
        private boolean confirmCalled;

        private RecordingPrompts(boolean confirm) {
            this.confirm = confirm;
        }

        @Override
        public void showSelectTraceMessage(com.intellij.openapi.project.Project project) {
        }

        @Override
        public boolean confirmDetectedLink(com.intellij.openapi.project.Project project, String sourceDisplayName, String targetDisplayName) {
            confirmCalled = true;
            return confirm;
        }
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.PluginDescriptorTest --tests com.zimaai.codetrace.actions.AddToCodeTraceHandlerTest`

预期：FAIL，编译错误会指出 `CodeTraceProjectService`、`TraceNodeCaptureService`、`AddToCodeTraceHandler`、`TraceUserPrompts`、`AddToCodeTraceAction` 不存在，`plugin.xml` 也没有 action/service 注册。

- [ ] **步骤 3：实现项目级服务、PSI 捕获和 editor popup action**

`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceProjectService.java`：

```java
package com.zimaai.codetrace.toolwindow;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.zimaai.codetrace.navigation.CodeNavigationService;
import com.zimaai.codetrace.storage.TraceJsonMapper;
import com.zimaai.codetrace.storage.TraceStorageService;

@Service(Service.Level.PROJECT)
public final class CodeTraceProjectService {
    private final CodeTraceController controller;
    private Runnable refreshCallback;

    public CodeTraceProjectService(Project project) {
        TraceStorageService storage = new TraceStorageService(project, new TraceJsonMapper());
        CodeNavigationService navigation = new CodeNavigationService(project);
        this.controller = new CodeTraceController(storage, navigation::navigate);
    }

    public CodeTraceController controller() {
        return controller;
    }

    public void registerRefreshCallback(Runnable refreshCallback) {
        this.refreshCallback = refreshCallback;
    }

    public void refreshToolWindowIfPresent() {
        if (refreshCallback != null) {
            refreshCallback.run();
        }
    }
}
```

`src/main/java/com/zimaai/codetrace/actions/TraceNodeCaptureService.java`：

```java
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
```

`src/main/java/com/zimaai/codetrace/actions/PsiTraceNodeCaptureService.java`：

```java
package com.zimaai.codetrace.actions;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.zimaai.codetrace.model.TraceNode;
import java.util.Optional;
import java.util.UUID;

public final class PsiTraceNodeCaptureService implements TraceNodeCaptureService {
    @Override
    public TraceNode captureCurrentLine(Project project, Editor editor, PsiFile psiFile) {
        int lineNumber = editor.getCaretModel().getLogicalPosition().line;
        return buildNodeFromLine(psiFile, editor.getDocument(), lineNumber, findCallableOwner(psiFile.findElementAt(editor.getCaretModel().getOffset())));
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
        int line = psiFile.getViewProvider().getDocument().getLineNumber(target.getTextRange().getStartOffset());
        return Optional.of(buildNodeFromLine(psiFile, psiFile.getViewProvider().getDocument(), line, target));
    }

    private static TraceNode buildNodeFromLine(PsiFile psiFile, Document document, int zeroBasedLine, PsiElement callable) {
        int start = document.getLineStartOffset(zeroBasedLine);
        int end = document.getLineEndOffset(zeroBasedLine);
        String lineText = document.getText(new com.intellij.openapi.util.TextRange(start, end)).replace('\r', ' ').replace('\n', ' ');
        VirtualFile file = psiFile.getVirtualFile();
        String qualifiedName = callable == null ? "" : callable.toString();
        String signature = callable == null ? "" : callable.getText().replace('\r', ' ').replace('\n', ' ');
        String navigationHint = qualifiedName;
        return new TraceNode(
                "node-" + UUID.randomUUID(),
                lineText,
                qualifiedName,
                signature,
                file == null ? "" : file.getPath(),
                zeroBasedLine + 1,
                psiFile.getLanguage().getID(),
                "",
                navigationHint);
    }

    private static PsiElement findCallableOwner(PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            String className = current.getClass().getSimpleName().toLowerCase();
            if (className.contains("method") || className.contains("function") || className.contains("lambda")) {
                return current;
            }
            current = current.getParent();
        }
        return PsiTreeUtil.getParentOfType(element, PsiElement.class, false);
    }
}
```

`src/main/java/com/zimaai/codetrace/actions/TraceUserPrompts.java`：

```java
package com.zimaai.codetrace.actions;

import com.intellij.openapi.project.Project;

public interface TraceUserPrompts {
    void showSelectTraceMessage(Project project);
    boolean confirmDetectedLink(Project project, String sourceDisplayName, String targetDisplayName);
}
```

`src/main/java/com/zimaai/codetrace/actions/SwingTraceUserPrompts.java`：

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
    public boolean confirmDetectedLink(Project project, String sourceDisplayName, String targetDisplayName) {
        int choice = JOptionPane.showConfirmDialog(
                null,
                "Also add target and create link?\nSource: " + sourceDisplayName + "\nTarget: " + targetDisplayName,
                "Detected target",
                JOptionPane.YES_NO_OPTION);
        return choice == JOptionPane.YES_OPTION;
    }
}
```

`src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceHandler.java`：

```java
package com.zimaai.codetrace.actions;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.toolwindow.CodeTraceController;
import java.util.Optional;

public final class AddToCodeTraceHandler {
    private final CodeTraceController controller;
    private final TraceNodeCaptureService captureService;
    private final TraceUserPrompts prompts;
    private final Runnable refreshUi;

    public AddToCodeTraceHandler(
            CodeTraceController controller,
            TraceNodeCaptureService captureService,
            TraceUserPrompts prompts,
            Runnable refreshUi) {
        this.controller = controller;
        this.captureService = captureService;
        this.prompts = prompts;
        this.refreshUi = refreshUi;
    }

    public void handle(Project project, Editor editor, PsiFile psiFile) {
        if (controller.state().currentFileName() == null) {
            prompts.showSelectTraceMessage(project);
            return;
        }
        TraceNode source = captureService.captureCurrentLine(project, editor, psiFile);
        int sourceIndex = controller.addOrReuseNode(source);
        Optional<TraceNode> detectedTarget = captureService.detectTarget(project, editor, psiFile);
        if (detectedTarget.isPresent()
                && prompts.confirmDetectedLink(project, source.displayName(), detectedTarget.get().displayName())) {
            int targetIndex = controller.addOrReuseNode(detectedTarget.get());
            String sourceId = controller.state().currentDocument().nodes().get(sourceIndex).id();
            String targetId = controller.state().currentDocument().nodes().get(targetIndex).id();
            controller.setPendingLinkSource(sourceId);
            controller.linkPendingSourceTo(targetId, TraceLinkKind.DETECTED);
        }
        refreshUi.run();
    }
}
```

`src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceAction.java`：

```java
package com.zimaai.codetrace.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.zimaai.codetrace.toolwindow.CodeTraceProjectService;
import org.jetbrains.annotations.NotNull;

public final class AddToCodeTraceAction extends AnAction {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        boolean enabled = project != null
                && event.getData(CommonDataKeys.EDITOR) != null
                && project.getService(CodeTraceProjectService.class).controller().state().currentFileName() != null;
        event.getPresentation().setEnabledAndVisible(enabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        var editor = event.getData(CommonDataKeys.EDITOR);
        if (project == null || editor == null) {
            return;
        }
        var psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (psiFile == null) {
            return;
        }
        CodeTraceProjectService service = project.getService(CodeTraceProjectService.class);
        AddToCodeTraceHandler handler = new AddToCodeTraceHandler(
                service.controller(),
                new PsiTraceNodeCaptureService(),
                new SwingTraceUserPrompts(),
                service::refreshToolWindowIfPresent);
        handler.handle(project, editor, psiFile);
    }
}
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
        CodeTraceProjectService service = project.getService(CodeTraceProjectService.class);
        CodeTracePanel panel = new CodeTracePanel(service.controller());
        service.registerRefreshCallback(panel::reloadFromDisk);
        panel.reloadFromDisk();
        var content = ContentFactory.getInstance().createContent(panel.getComponent(), "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
```

`src/main/resources/META-INF/plugin.xml`：

```xml
<idea-plugin>
    <id>com.zimaai.codetrace</id>
    <name>code-trace</name>
    <vendor email="support@example.com">zimaai</vendor>

    <depends>com.intellij.modules.platform</depends>
    <depends optional="true">com.intellij.modules.lang</depends>

    <extensions defaultExtensionNs="com.intellij">
        <projectService serviceImplementation="com.zimaai.codetrace.toolwindow.CodeTraceProjectService"/>
        <toolWindow
                id="code-trace"
                anchor="left"
                factoryClass="com.zimaai.codetrace.toolwindow.CodeTraceToolWindowFactory"
                icon="AllIcons.Toolwindows.ToolWindowMessages"/>
    </extensions>

    <actions>
        <action
                id="com.zimaai.codetrace.actions.AddToCodeTraceAction"
                class="com.zimaai.codetrace.actions.AddToCodeTraceAction"
                text="Add to code-trace">
            <add-to-group group-id="EditorPopupMenu" anchor="last"/>
        </action>
    </actions>
</idea-plugin>
```

- [ ] **步骤 4：运行测试验证通过**

运行：`.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.PluginDescriptorTest --tests com.zimaai.codetrace.actions.AddToCodeTraceHandlerTest`

预期：PASS，`plugin.xml` 注册了 project service 和 popup action，handler 能把 source 节点写入当前 trace，并在用户确认时追加 target 与 `DETECTED` 链接。

- [ ] **步骤 5：Commit**

```bash
git add src/main/resources/META-INF/plugin.xml src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceProjectService.java src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceToolWindowFactory.java src/main/java/com/zimaai/codetrace/actions src/test/java/com/zimaai/codetrace/toolwindow/PluginDescriptorTest.java src/test/java/com/zimaai/codetrace/actions/AddToCodeTraceHandlerTest.java
git commit -m "feat: add editor popup append-to-trace workflow"
```

## Task 5: 清理已废弃的 recording/history 路径并完成回归验证

**文件：**
- 删除：`src/main/java/com/zimaai/codetrace/recording/TraceRecordingService.java`
- 删除：`src/main/java/com/zimaai/codetrace/recording/TraceRecordingSession.java`
- 删除：`src/main/java/com/zimaai/codetrace/recording/TraceableNavigationTarget.java`
- 删除：`src/main/java/com/zimaai/codetrace/recording/IdeNavigationListener.java`
- 删除：`src/test/java/com/zimaai/codetrace/recording/TraceRecordingServiceTest.java`
- 修改：`docs/superpowers/plans/2026-05-29-code-trace-manual-smoke-checklist.md`
- 测试：现有所有 `src/test/java/com/zimaai/codetrace/**` 测试

- [ ] **步骤 1：更新手工冒烟清单到新工作流**

`docs/superpowers/plans/2026-05-29-code-trace-manual-smoke-checklist.md` 改成：

```markdown
# Code-Trace 单版本节点链接冒烟检查清单

## 启动

1. 在项目根目录执行：`.\gradlew.bat runIde`
2. 等待 Sandbox IDEA 启动。
3. 在 Sandbox IDEA 中打开任意含源码的项目。

## Tool Window 基础

1. 左侧工具栏可见 `code-trace`。
2. 打开后可见：
   - 顶部按钮：`Refresh / Save Trace Note / Save Node Note / Set as Source / Link To Here / Unlink`
   - 左侧文件列表
   - 右侧 trace note、节点列表、node note
3. 不再出现：
   - `Start Recording`
   - `Stop Recording`
   - `Save`
   - `Add Node`
   - `History`

## 文件管理

1. 新建、重命名、复制、删除、刷新 JSON 都正常工作。
2. 刷新后仍保持当前文档选中。
3. 外部修改 v1 JSON 后点击 `Refresh`，界面正常显示迁移后的节点列表。

## Note 保存

1. 修改 `trace note` 后，`Save Trace Note` 变为可点击。
2. 点击后关闭再打开文件，内容仍然存在。
3. 修改某个 `node note` 后，`Save Node Note` 变为可点击。
4. 点击后刷新文件，内容仍然存在。

## 节点与链接

1. 节点列表每一项只显示一行代码文本。
2. 选择一个节点点击 `Set as Source`，状态区显示 source id。
3. 选择另一个节点点击 `Link To Here`，两个节点出现不同的链接样式。
4. 点击 `Unlink` 后样式消失。
5. 对已链接节点执行 `Move Up / Move Down / Delete`，两个节点按一对处理。

## 编辑器右键

1. 在编辑器当前行右键可见 `Add to code-trace`。
2. 先在 Tool Window 中选中一个 JSON，再执行右键动作。
3. 当前行被加入节点列表，`displayName` 与编辑器当前行文本一致。
4. 如果弹出 target 确认并选择 `Yes`，会额外新增 target 节点和一条 `DETECTED` 链接。

## 跳转验证

1. 双击节点，编辑器跳到对应文件和行。
2. 外部改代码后，无法跳转时显示明确错误而不是崩溃。
```

- [ ] **步骤 2：删除录制相关实现和测试**

运行：

```bash
git rm src/main/java/com/zimaai/codetrace/recording/TraceRecordingService.java src/main/java/com/zimaai/codetrace/recording/TraceRecordingSession.java src/main/java/com/zimaai/codetrace/recording/TraceableNavigationTarget.java src/main/java/com/zimaai/codetrace/recording/IdeNavigationListener.java src/test/java/com/zimaai/codetrace/recording/TraceRecordingServiceTest.java
```

预期：这些文件从索引中删除，`git status --short` 显示为 `D`。

- [ ] **步骤 3：运行完整测试套件**

运行：`.\gradlew.bat test`

预期：PASS，所有 storage/controller/panel/action/plugin descriptor 测试通过，且不会再编译 recording 包。

- [ ] **步骤 4：运行插件配置校验并手工冒烟**

运行：`.\gradlew.bat verifyPluginProjectConfiguration`

预期：PASS，`plugin.xml`、服务注册和 action 注册都符合平台要求。

运行：`.\gradlew.bat runIde`

手工执行上面的 [2026-05-29-code-trace-manual-smoke-checklist.md](D:\Develop\Projects\code-trace\docs\superpowers\plans\2026-05-29-code-trace-manual-smoke-checklist.md) 检查项，重点确认：

- 右键 `Add to code-trace` 只在有当前 trace 时可用
- 节点列表文本只显示 `displayName`
- `trace note` 与 `node note` 按钮只在内容变化后可点击
- linked pair 的移动和删除按设计执行

- [ ] **步骤 5：Commit**

```bash
git add docs/superpowers/plans/2026-05-29-code-trace-manual-smoke-checklist.md
git commit -m "refactor: remove recording workflow and verify single-version trace flow"
```

## 自检清单

### 规格覆盖度

- schema v2、v1 迁移、单版本文档：Task 1
- 立即持久化、note 单独保存、节点链接约束：Task 2
- Tool Window 去掉 recording/history/save/add node，列表只显示 `displayName`：Task 3
- editor popup `Add to code-trace`、候选 target、手动链接：Task 4
- 录制代码和测试删除、冒烟验证、插件配置校验：Task 5

### 占位符扫描

- 本计划未保留任何占位符式措辞或“后补细节”步骤。
- 每个任务都给出了具体文件、测试代码、命令和预期结果。

### 类型一致性

- 文档模型统一使用 `TraceDocument + TraceNode + TraceLink + TraceLinkKind`。
- 控制器统一使用 `saveDescription`、`saveNodeNote`、`setPendingLinkSource`、`linkPendingSourceTo`、`moveNodeOrPair`、`deleteNodeOrPair`。
- editor popup 统一使用 `TraceNodeCaptureService`、`TraceUserPrompts`、`AddToCodeTraceHandler`。
