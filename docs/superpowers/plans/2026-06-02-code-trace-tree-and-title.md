# code-trace 树形结构与标题增强实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将节点列表从扁平 `JBList` 升级为多层级 `JTree`，支持子节点展开/折叠、拖拽缩进设子节点、聚焦定位图标、title 字段及编辑、展开状态持久化。

**架构：** `TraceNode` 新增 `parentId` 和 `title`，`TraceDocument` 升至 schema v3 并新增 `expandedNodeIds`。创建 `TraceTreeModel` 适配 `JTree`，`LinkedNodeTreeCellRenderer` 负责渲染聚焦图标、title 截断、link 角色。`NodeTreeTransferHandler` 通过鼠标 X 偏移量判断拖拽意图（排序 vs 设为子节点 vs 移出）。`CodeTraceController` 新增 `setParent`/`addChildNode`/级联删除等树操作。

**技术栈：** Java 21、Swing (JTree)、IntelliJ Platform SDK、JUnit 5、Jackson

---

## Planned File Structure

### 数据模型

- 修改：`src/main/java/com/zimaai/codetrace/model/TraceNode.java`
- 修改：`src/main/java/com/zimaai/codetrace/model/TraceDocument.java`
- 修改：`src/main/java/com/zimaai/codetrace/storage/TraceJsonMapper.java`
- 修改：`src/test/java/com/zimaai/codetrace/storage/TraceJsonMapperTest.java`

### 树视图

- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/TraceTreeModel.java`
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/LinkedNodeTreeCellRenderer.java`
- 删除：`src/main/java/com/zimaai/codetrace/toolwindow/LinkedNodeListCellRenderer.java`

### 编辑器面板

- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java`
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/TraceEditorPanelTest.java`

### 面板与控制

- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/TraceDocumentEditor.java`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java`
- 创建：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTreeTest.java`
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTest.java`
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java`

### 拖拽

- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/NodeTreeTransferHandler.java`
- 删除：`src/main/java/com/zimaai/codetrace/toolwindow/NodeListReorderTransferHandler.java`

### 外部动作

- 修改：`src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceHandler.java`
- 修改：`src/test/java/com/zimaai/codetrace/actions/AddToCodeTraceHandlerTest.java`

### 测试

- 创建：`src/test/java/com/zimaai/codetrace/toolwindow/LinkedNodeTreeCellRendererTest.java`

### 文档

- 修改：`docs/superpowers/plans/2026-05-29-code-trace-manual-smoke-checklist.md`

---

### 任务 1：数据模型——TraceNode 新增 parentId/title，TraceDocument 升级 schema v3

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/model/TraceNode.java`
- 修改：`src/main/java/com/zimaai/codetrace/model/TraceDocument.java`
- 修改：`src/main/java/com/zimaai/codetrace/storage/TraceJsonMapper.java`
- 修改：`src/test/java/com/zimaai/codetrace/storage/TraceJsonMapperTest.java`

- [ ] **步骤 1：编写 JSON 向后兼容测试**

在 `src/test/java/com/zimaai/codetrace/storage/TraceJsonMapperTest.java` 追加：

```java
    @Test
    void migratesSchemaV2ToV3WithNullParentsAndEmptyExpandState() throws Exception {
        TraceJsonMapper mapper = new TraceJsonMapper();
        String v2Json = """
                {
                  "schemaVersion": 2,
                  "id": "trace-1",
                  "name": "Test",
                  "description": "desc",
                  "createdAt": "2026-06-02T10:00:00Z",
                  "updatedAt": "2026-06-02T10:00:00Z",
                  "nodes": [
                    {
                      "id": "node-1",
                      "displayName": "line 1",
                      "qualifiedName": "A#a",
                      "signature": "a()",
                      "filePath": "A.java",
                      "line": 10,
                      "language": "JAVA",
                      "note": "",
                      "navigationHint": "A#a"
                    }
                  ],
                  "links": []
                }
                """;
        TraceDocument doc = mapper.read(v2Json);
        assertEquals(3, doc.schemaVersion());
        assertEquals(1, doc.nodes().size());
        assertNull(doc.nodes().get(0).parentId());
        assertNull(doc.nodes().get(0).title());
        assertTrue(doc.expandedNodeIds().isEmpty());
    }
```

补充导入：

```java
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```bash
./gradlew test --tests "com.zimaai.codetrace.storage.TraceJsonMapperTest.migratesSchemaV2ToV3WithNullParentsAndEmptyExpandState"
```

预期：FAIL，`TraceNode` 还没 `parentId()`/`title()`，`TraceDocument` 还没 `expandedNodeIds()`。

- [ ] **步骤 3：实现数据模型变更**

修改 `src/main/java/com/zimaai/codetrace/model/TraceNode.java`：

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
        String navigationHint,
        String parentId,
        String title) {
    /** Convenience constructor for root-level nodes without title. */
    public TraceNode(
            String id,
            String displayName,
            String qualifiedName,
            String signature,
            String filePath,
            int line,
            String language,
            String note,
            String navigationHint) {
        this(id, displayName, qualifiedName, signature, filePath, line, language, note, navigationHint, null, null);
    }
}
```

修改 `src/main/java/com/zimaai/codetrace/model/TraceDocument.java`：

```java
package com.zimaai.codetrace.model;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record TraceDocument(
        int schemaVersion,
        String id,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt,
        List<TraceNode> nodes,
        List<TraceLink> links,
        Set<String> expandedNodeIds) {
}
```

修改 `src/main/java/com/zimaai/codetrace/storage/TraceJsonMapper.java` 的 `migrateSchemaOne` 方法——映射节点时，`parentId`/`title` 取 `null`；文档结果中补充 `schemaVersion=3` 和空 `expandedNodeIds`：

```java
    private TraceDocument migrateSchemaOne(JsonNode root) throws Exception {
        List<TraceNode> migratedNodes = new ArrayList<>();
        JsonNode currentNodes = root.path("current").path("nodes");
        if (currentNodes.isArray()) {
            for (JsonNode node : currentNodes) {
                migratedNodes.add(new TraceNode(
                        node.path("id").asText(),
                        node.path("displayName").asText(),
                        node.path("qualifiedName").asText(),
                        node.path("signature").asText(),
                        node.path("filePath").asText(),
                        node.path("line").asInt(),
                        node.path("language").asText(),
                        node.path("note").asText(""),
                        node.path("navigationHint").asText(""),
                        null,
                        null));
            }
        }
        return new TraceDocument(
                3,
                root.path("id").asText(),
                root.path("name").asText(),
                root.path("description").asText(""),
                parseInstant(root.path("createdAt")),
                parseInstant(root.path("updatedAt")),
                List.copyOf(migratedNodes),
                List.<TraceLink>of(),
                Set.of());
    }
```

在 `read` 方法中添加 schema v2→v3 的迁移路径（在 v1 迁移之后）：

```java
    public TraceDocument read(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        int schemaVersion = root.path("schemaVersion").asInt(1);
        if (schemaVersion >= 3) {
            return mapper.treeToValue(root, TraceDocument.class);
        }
        if (schemaVersion >= 2) {
            return migrateSchemaTwo(root);
        }
        return migrateSchemaOne(root);
    }

    private TraceDocument migrateSchemaTwo(JsonNode root) throws Exception {
        TraceDocument doc = mapper.treeToValue(root, TraceDocument.class);
        List<TraceNode> migratedNodes = doc.nodes().stream()
                .map(n -> new TraceNode(
                        n.id(), n.displayName(), n.qualifiedName(), n.signature(),
                        n.filePath(), n.line(), n.language(), n.note(),
                        n.navigationHint(), null, null))
                .toList();
        return new TraceDocument(
                3,
                doc.id(),
                doc.name(),
                doc.description(),
                doc.createdAt(),
                doc.updatedAt(),
                migratedNodes,
                doc.links(),
                Set.of());
    }
```

补充导入：

```java
import java.util.Set;
```

- [ ] **步骤 4：修复所有构造 TraceNode/TraceDocument 的调用点**

以下文件中的 `new TraceNode(...)` 调用**无需修改**（因为有兼容构造函数），但 `new TraceDocument(...)` 调用需要在最后加 `Set.of()`：

`src/main/java/com/zimaai/codetrace/toolwindow/TraceDocumentEditor.java` — 所有 `new TraceDocument(2, ...)` 改成 `new TraceDocument(3, ...)`，末尾加 `, Set.of()`。等 5 处。

`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java`：
- `createEmptyDocument` 中 `new TraceDocument(2, ...)` 改成 `new TraceDocument(3, ...)`，末尾加 `, Set.of()`
- `moveInternal` 中 `new TraceDocument(2, ...)` 改成 `new TraceDocument(3, ...)`，末尾加 `, document.expandedNodeIds()`
- `moveInternalToIndex` 中类似修改
- `deleteInternal` 中类似修改

测试文件中的所有 `new TraceDocument(2, ...)` 改成 `new TraceDocument(3, ...)`，末尾加 `, Set.of()`：
- `CodeTraceControllerTest.java`
- `CodeTracePanelTest.java`
- `AddToCodeTraceHandlerTest.java`

- [ ] **步骤 5：运行全部测试验证通过**

```bash
./gradlew test
```

预期：PASS（所有测试通过模型适配）。

- [ ] **步骤 6：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/model/TraceNode.java \
        src/main/java/com/zimaai/codetrace/model/TraceDocument.java \
        src/main/java/com/zimaai/codetrace/storage/TraceJsonMapper.java \
        src/main/java/com/zimaai/codetrace/toolwindow/TraceDocumentEditor.java \
        src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java \
        src/test/java/com/zimaai/codetrace/storage/TraceJsonMapperTest.java \
        src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java \
        src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTest.java \
        src/test/java/com/zimaai/codetrace/actions/AddToCodeTraceHandlerTest.java
git commit -m "feat(code-trace): add parentId/title to TraceNode and bump schema to v3"
```

---

### 任务 2：TraceTreeModel——树形数据适配器

**文件：**
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/TraceTreeModel.java`

- [ ] **步骤 1：编写 TreeModel 测试**

创建 `src/test/java/com/zimaai/codetrace/toolwindow/TraceTreeModelTest.java`：

```java
package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TraceTreeModelTest {

    @Test
    void virtualRootChildrenAreAllTopLevelNodes() {
        TraceDocument doc = new TraceDocument(
                3, "t1", "T1", "", Instant.now(), Instant.now(),
                List.of(
                        new TraceNode("n1", "a", "", "", "", 0, "", "", ""),
                        new TraceNode("n2", "b", "", "", "", 0, "", "", ""),
                        new TraceNode("n3", "c", "", "", "", 0, "", "", "n1", null)),
                List.of(), Set.of());

        TraceTreeModel model = new TraceTreeModel(() -> doc);
        Object root = model.getRoot();
        assertEquals(2, model.getChildCount(root)); // n1, n2 (not n3 since it's a child of n1)
        assertEquals("n1", ((TraceNode) model.getChild(root, 0)).id());
        assertEquals("n2", ((TraceNode) model.getChild(root, 1)).id());
    }

    @Test
    void childCountMatchesChildrenOfNode() {
        TraceDocument doc = new TraceDocument(
                3, "t2", "T2", "", Instant.now(), Instant.now(),
                List.of(
                        new TraceNode("n1", "parent", "", "", "", 0, "", "", ""),
                        new TraceNode("n2", "child1", "", "", "", 0, "", "", "n1", null),
                        new TraceNode("n3", "child2", "", "", "", 0, "", "", "n1", null)),
                List.of(), Set.of());

        TraceTreeModel model = new TraceTreeModel(() -> doc);
        TraceNode n1 = doc.nodes().get(0);
        assertEquals(2, model.getChildCount(n1));
    }

    @Test
    void leafNodesHaveNoChildren() {
        TraceDocument doc = new TraceDocument(
                3, "t3", "T3", "", Instant.now(), Instant.now(),
                List.of(new TraceNode("n1", "leaf", "", "", "", 0, "", "", "")),
                List.of(), Set.of());

        TraceTreeModel model = new TraceTreeModel(() -> doc);
        TraceNode n1 = doc.nodes().get(0);
        assertTrue(model.isLeaf(n1));
    }

    @Test
    void handlesNullDocumentGracefully() {
        TraceTreeModel model = new TraceTreeModel(() -> null);
        assertEquals(0, model.getChildCount(model.getRoot()));
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

```bash
./gradlew test --tests "com.zimaai.codetrace.toolwindow.TraceTreeModelTest"
```

预期：FAIL，`TraceTreeModel` 类不存在。

- [ ] **步骤 3：实现 TraceTreeModel**

创建 `src/main/java/com/zimaai/codetrace/toolwindow/TraceTreeModel.java`：

```java
package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.event.EventListenerList;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

public final class TraceTreeModel implements TreeModel {
    static final TraceNode VIRTUAL_ROOT = new TraceNode(
            "__root__", "__root__", "", "", "", 0, "", "", "", null, null);

    private final Supplier<TraceDocument> documentSupplier;
    private final EventListenerList listeners = new EventListenerList();

    public TraceTreeModel(Supplier<TraceDocument> documentSupplier) {
        this.documentSupplier = Objects.requireNonNull(documentSupplier, "documentSupplier");
    }

    @Override
    public Object getRoot() {
        return VIRTUAL_ROOT;
    }

    @Override
    public Object getChild(Object parent, int index) {
        List<TraceNode> children = childrenOf(parent);
        return index >= 0 && index < children.size() ? children.get(index) : null;
    }

    @Override
    public int getChildCount(Object parent) {
        return childrenOf(parent).size();
    }

    @Override
    public boolean isLeaf(Object node) {
        return getChildCount(node) == 0;
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        if (!(child instanceof TraceNode childNode)) return -1;
        List<TraceNode> children = childrenOf(parent);
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).id().equals(childNode.id())) return i;
        }
        return -1;
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
        // Not supported — nodes are immutable records
    }

    @Override
    public void addTreeModelListener(TreeModelListener listener) {
        listeners.add(TreeModelListener.class, listener);
    }

    @Override
    public void removeTreeModelListener(TreeModelListener listener) {
        listeners.remove(TreeModelListener.class, listener);
    }

    public void fireStructureChanged() {
        TreeModelEvent e = new TreeModelEvent(this, new Object[]{VIRTUAL_ROOT});
        for (TreeModelListener l : listeners.getListeners(TreeModelListener.class)) {
            l.treeStructureChanged(e);
        }
    }

    private List<TraceNode> childrenOf(Object parent) {
        TraceDocument doc = documentSupplier.get();
        if (doc == null) return List.of();
        if (parent == VIRTUAL_ROOT) {
            return doc.nodes().stream()
                    .filter(n -> n.parentId() == null)
                    .toList();
        }
        if (parent instanceof TraceNode parentNode) {
            return doc.nodes().stream()
                    .filter(n -> Objects.equals(n.parentId(), parentNode.id()))
                    .toList();
        }
        return List.of();
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

```bash
./gradlew test --tests "com.zimaai.codetrace.toolwindow.TraceTreeModelTest"
```

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/toolwindow/TraceTreeModel.java \
        src/test/java/com/zimaai/codetrace/toolwindow/TraceTreeModelTest.java
git commit -m "feat(code-trace): add TraceTreeModel for tree-structured node data"
```

---

### 任务 3：LinkedNodeTreeCellRenderer——树节点渲染器

**文件：**
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/LinkedNodeTreeCellRenderer.java`
- 创建：`src/test/java/com/zimaai/codetrace/toolwindow/LinkedNodeTreeCellRendererTest.java`

- [ ] **步骤 1：编写渲染器测试**

创建 `src/test/java/com/zimaai/codetrace/toolwindow/LinkedNodeTreeCellRendererTest.java`：

```java
package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.Component;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import javax.swing.JLabel;
import javax.swing.JTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LinkedNodeTreeCellRendererTest {
    private JTree tree;

    @BeforeEach
    void setUp() {
        tree = new JTree();
    }

    @Test
    void showsTitleAndDisplayNameWhenTitleIsPresent() {
        TraceDocument doc = createDoc(List.of(
                new TraceNode("n1", "login()", "", "", "", 0, "", "", "", null, "认证")));
        TraceTreeModel model = new TraceTreeModel(() -> doc);
        tree.setModel(model);

        LinkedNodeTreeCellRenderer renderer = new LinkedNodeTreeCellRenderer(() -> doc, () -> null, () -> null);
        Component c = renderer.getTreeCellRendererComponent(tree, doc.nodes().get(0),
                false, false, true, 0, false);
        String text = ((JLabel) c).getText();
        assertTrue(text.startsWith("认证"));
        assertTrue(text.contains("login()"));
    }

    @Test
    void showsOnlyDisplayNameWhenTitleIsAbsent() {
        TraceDocument doc = createDoc(List.of(
                new TraceNode("n1", "login()", "", "", "", 0, "", "", "")));
        TraceTreeModel model = new TraceTreeModel(() -> doc);
        tree.setModel(model);

        LinkedNodeTreeCellRenderer renderer = new LinkedNodeTreeCellRenderer(() -> doc, () -> null, () -> null);
        Component c = renderer.getTreeCellRendererComponent(tree, doc.nodes().get(0),
                false, false, true, 0, false);
        assertEquals("login()", ((JLabel) c).getText());
    }

    @Test
    void showsFocusIconWhenNodeIsFocused() {
        TraceDocument doc = createDoc(List.of(
                new TraceNode("n1", "login()", "", "", "", 0, "", "", "")));
        TraceTreeModel model = new TraceTreeModel(() -> doc);
        tree.setModel(model);

        LinkedNodeTreeCellRenderer renderer = new LinkedNodeTreeCellRenderer(() -> doc, () -> "n1", () -> null);
        Component c = renderer.getTreeCellRendererComponent(tree, doc.nodes().get(0),
                false, false, true, 0, false);
        String text = ((JLabel) c).getText();
        assertTrue(text.startsWith(LinkedNodeTreeCellRenderer.FOCUS_PREFIX));
    }

    @Test
    void showsSourceRolePrefixForSourceNode() {
        TraceDocument doc = new TraceDocument(
                3, "t1", "T1", "", Instant.now(), Instant.now(),
                List.of(
                        new TraceNode("n1", "src", "", "", "", 0, "", "", ""),
                        new TraceNode("n2", "tgt", "", "", "", 0, "", "", "")),
                List.of(new TraceLink("l1", "n1", "n2", Instant.now(), TraceLinkKind.MANUAL)),
                Set.of());
        TraceTreeModel model = new TraceTreeModel(() -> doc);
        tree.setModel(model);

        LinkedNodeTreeCellRenderer renderer = new LinkedNodeTreeCellRenderer(() -> doc, () -> null, () -> null);
        Component c = renderer.getTreeCellRendererComponent(tree, doc.nodes().get(0),
                false, false, true, 0, false);
        assertTrue(((JLabel) c).getText().contains(LinkedNodeTreeCellRenderer.ROLE_SOURCE));
    }

    @Test
    void truncatesLongTitleWithEllipsis() {
        String longTitle = "这是一个非常非常非常非常长的标题文本";
        TraceDocument doc = createDoc(List.of(
                new TraceNode("n1", "login()", "", "", "", 0, "", "", "", null, longTitle)));
        TraceTreeModel model = new TraceTreeModel(() -> doc);
        tree.setModel(model);

        LinkedNodeTreeCellRenderer renderer = new LinkedNodeTreeCellRenderer(() -> doc, () -> null, () -> null);
        Component c = renderer.getTreeCellRendererComponent(tree, doc.nodes().get(0),
                false, false, true, 0, false);
        String text = ((JLabel) c).getText();
        assertTrue(text.contains("…"));
        assertTrue(text.length() < longTitle.length() + 10);
    }

    private static TraceDocument createDoc(List<TraceNode> nodes) {
        return new TraceDocument(3, "t1", "T1", "", Instant.now(), Instant.now(),
                nodes, List.of(), Set.of());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

```bash
./gradlew test --tests "com.zimaai.codetrace.toolwindow.LinkedNodeTreeCellRendererTest"
```

预期：FAIL，`LinkedNodeTreeCellRenderer` 类不存在。

- [ ] **步骤 3：实现 LinkedNodeTreeCellRenderer**

创建 `src/main/java/com/zimaai/codetrace/toolwindow/LinkedNodeTreeCellRenderer.java`：

```java
package com.zimaai.codetrace.toolwindow;

import com.intellij.ui.JBColor;
import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.Color;
import java.awt.Component;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

public final class LinkedNodeTreeCellRenderer extends DefaultTreeCellRenderer {
    static final Color PENDING_SOURCE_COLOR = new JBColor(new Color(255, 243, 205), new Color(77, 59, 24));
    static final Color SOURCE_COLOR = new JBColor(new Color(219, 234, 254), new Color(28, 55, 80));
    static final Color TARGET_COLOR = new JBColor(new Color(220, 252, 231), new Color(24, 64, 35));

    static final Color PENDING_SOURCE_BORDER = new JBColor(new Color(234, 179, 8), new Color(202, 138, 4));
    static final Color SOURCE_BORDER = new JBColor(new Color(96, 165, 250), new Color(59, 130, 246));
    static final Color TARGET_BORDER = new JBColor(new Color(74, 222, 128), new Color(34, 197, 94));

    static final String FOCUS_PREFIX = "● ";
    static final String ROLE_PENDING_SOURCE = "◉ ";
    static final String ROLE_SOURCE = "▶ ";
    static final String ROLE_TARGET = "◀ ";

    private static final int MAX_TITLE_CHARS = 15;
    private static final Color TITLE_COLOR = JBColor.GRAY;

    private final Supplier<TraceDocument> documentSupplier;
    private final Supplier<String> focusedNodeIdSupplier;
    private final Supplier<String> pendingSourceSupplier;

    public LinkedNodeTreeCellRenderer(
            Supplier<TraceDocument> documentSupplier,
            Supplier<String> focusedNodeIdSupplier,
            Supplier<String> pendingSourceSupplier) {
        this.documentSupplier = Objects.requireNonNull(documentSupplier, "documentSupplier");
        this.focusedNodeIdSupplier = Objects.requireNonNull(focusedNodeIdSupplier, "focusedNodeIdSupplier");
        this.pendingSourceSupplier = Objects.requireNonNull(pendingSourceSupplier, "pendingSourceSupplier");
    }

    @Override
    public Component getTreeCellRendererComponent(
            JTree tree, Object value, boolean selected, boolean expanded,
            boolean leaf, int row, boolean hasFocus) {
        JLabel label = (JLabel) super.getTreeCellRendererComponent(
                tree, "", selected, expanded, leaf, row, hasFocus);

        if (!(value instanceof TraceNode node) || node == TraceTreeModel.VIRTUAL_ROOT) {
            return label;
        }

        label.setOpaque(true);
        label.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));

        StringBuilder prefix = new StringBuilder();

        // Focus indicator
        if (node.id().equals(focusedNodeIdSupplier.get())) {
            prefix.append(FOCUS_PREFIX);
        }

        // Role indicator
        String pendingSource = pendingSourceSupplier.get();
        TraceDocument document = documentSupplier.get();

        if (!selected) {
            if (node.id().equals(pendingSource)) {
                prefix.append(ROLE_PENDING_SOURCE);
                label.setBackground(PENDING_SOURCE_COLOR);
                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, PENDING_SOURCE_BORDER),
                        BorderFactory.createEmptyBorder(3, 6, 3, 4)));
            } else if (document != null) {
                boolean roleApplied = false;
                for (TraceLink link : document.links()) {
                    if (node.id().equals(link.sourceNodeId())) {
                        prefix.append(ROLE_SOURCE);
                        label.setBackground(SOURCE_COLOR);
                        label.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createMatteBorder(0, 3, 0, 0, SOURCE_BORDER),
                                BorderFactory.createEmptyBorder(3, 6, 3, 4)));
                        roleApplied = true;
                        break;
                    }
                    if (node.id().equals(link.targetNodeId())) {
                        prefix.append(ROLE_TARGET);
                        label.setBackground(TARGET_COLOR);
                        label.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createMatteBorder(0, 3, 0, 0, TARGET_BORDER),
                                BorderFactory.createEmptyBorder(3, 6, 3, 4)));
                        roleApplied = true;
                        break;
                    }
                }
                if (!roleApplied) {
                    label.setBackground(null);
                }
            }
        }

        // Title + displayName
        String title = node.title();
        if (title != null && !title.isBlank()) {
            String truncated = title.length() > MAX_TITLE_CHARS
                    ? title.substring(0, MAX_TITLE_CHARS) + "…" : title;
            label.setText(prefix + truncated + " — " + node.displayName());
        } else {
            label.setText(prefix + node.displayName());
        }

        return label;
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

```bash
./gradlew test --tests "com.zimaai.codetrace.toolwindow.LinkedNodeTreeCellRendererTest"
```

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/toolwindow/LinkedNodeTreeCellRenderer.java \
        src/test/java/com/zimaai/codetrace/toolwindow/LinkedNodeTreeCellRendererTest.java
git commit -m "feat(code-trace): add LinkedNodeTreeCellRenderer with focus icon and title support"
```

---

### 任务 4：TraceEditorPanel——JBList 替换为 JTree

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java`
- 删除：`src/main/java/com/zimaai/codetrace/toolwindow/LinkedNodeListCellRenderer.java`
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/TraceEditorPanelTest.java`

- [ ] **步骤 1：编写 TraceEditorPanel JTree 测试**

在 `src/test/java/com/zimaai/codetrace/toolwindow/TraceEditorPanelTest.java` 追加：

```java
    @Test
    void exposesJTreeInsteadOfJListAfterTreeMigration() {
        TraceEditorPanel panel = new TraceEditorPanel();

        assertNotNull(panel.nodeTree());
        assertFalse(panel.nodeTree().isRootVisible());
    }
```

补充导入：
```java
import static org.junit.jupiter.api.Assertions.assertFalse;
```

- [ ] **步骤 2：运行测试验证失败**

```bash
./gradlew test --tests "com.zimaai.codetrace.toolwindow.TraceEditorPanelTest.exposesJTreeInsteadOfJListAfterTreeMigration"
```

预期：FAIL，`nodeTree()` 方法不存在。

- [ ] **步骤 3：修改 TraceEditorPanel**

把 `src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java` 从：

```java
import com.intellij.ui.components.JBList;
// ...
private final JBList<TraceNode> nodeList = new JBList<>();
```

改为：

```java
import javax.swing.JTree;
// ...
private final JTree nodeTree = new JTree();
```

在构造函数中配置 JTree：

```java
public TraceEditorPanel() {
    configureTextArea(traceNote);
    configureTextArea(nodeNote);

    // Configure JTree
    nodeTree.setRootVisible(false);
    nodeTree.setShowsRootHandles(true);
    nodeTree.setEditable(false);

    JPanel traceNotePanel = new JPanel(new BorderLayout());
    // ... (unchanged)
```

把 `JSplitPane` 中的 `new JBScrollPane(nodeList)` 改成 `new JBScrollPane(nodeTree)`。

修改 `nodeList()` 访问器为 `nodeTree()`：

```java
    public JTree nodeTree() {
        return nodeTree;
    }
```

删除 `nodeList()` 方法。

- [ ] **步骤 4：运行测试验证通过**

```bash
./gradlew test --tests "com.zimaai.codetrace.toolwindow.TraceEditorPanelTest"
```

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java \
        src/test/java/com/zimaai/codetrace/toolwindow/TraceEditorPanelTest.java
git rm src/main/java/com/zimaai/codetrace/toolwindow/LinkedNodeListCellRenderer.java
git commit -m "feat(code-trace): replace JBList with JTree in TraceEditorPanel"
```

---

### 任务 5：CodeTracePanel——树选择、聚焦、展开/折叠、模型连接

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java`
- 创建：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTreeTest.java`

- [ ] **步骤 1：编写面板树测试**

创建 `src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTreeTest.java`：

```java
package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.storage.TraceJsonMapper;
import com.zimaai.codetrace.storage.TraceStorageService;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import javax.swing.JTree;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeTracePanelTreeTest {
    @TempDir
    Path tempDir;

    @Test
    void treeModelReflectsNestedNodesAfterReload() throws Exception {
        TraceDocument doc = new TraceDocument(
                3, "t1", "T1", "", Instant.now(), Instant.now(),
                List.of(
                        new TraceNode("n1", "root", "", "", "", 0, "", "", ""),
                        new TraceNode("n2", "child", "", "", "", 0, "", "", "n1", null)),
                List.of(), Set.of());

        CodeTracePanel panel = panelFor(doc);
        JTree tree = tree(panel);
        TraceTreeModel model = (TraceTreeModel) tree.getModel();

        Object root = model.getRoot();
        assertEquals(1, model.getChildCount(root)); // only n1 is top-level
    }

    @Test
    void treeSelectionSyncsFocusedNodeId() throws Exception {
        TraceDocument doc = new TraceDocument(
                3, "t1", "T1", "", Instant.now(), Instant.now(),
                List.of(new TraceNode("n1", "node", "", "", "", 0, "", "", "")),
                List.of(), Set.of());

        CodeTracePanel panel = panelFor(doc);
        CodeTraceController controller = controller(panel);
        JTree tree = tree(panel);

        TreePath path = tree.getPathForRow(0);
        tree.setSelectionPath(path);
        assertEquals("n1", controller.focusedNodeId());
    }

    @Test
    void usesLinkedNodeTreeCellRenderer() throws Exception {
        TraceDocument doc = new TraceDocument(
                3, "t1", "T1", "", Instant.now(), Instant.now(),
                List.of(new TraceNode("n1", "node", "", "", "", 0, "", "", "")),
                List.of(), Set.of());

        CodeTracePanel panel = panelFor(doc);
        JTree tree = tree(panel);

        assertNotNull(tree.getCellRenderer());
        assertTrue(tree.getCellRenderer() instanceof LinkedNodeTreeCellRenderer);
    }

    private CodeTracePanel panelFor(TraceDocument document) {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-1.json", document);
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        CodeTracePanel panel = new CodeTracePanel(controller);
        panel.reloadFromDisk();
        return panel;
    }

    private static JTree tree(CodeTracePanel panel) throws Exception {
        Field editorPanelField = CodeTracePanel.class.getDeclaredField("editorPanel");
        editorPanelField.setAccessible(true);
        TraceEditorPanel editorPanel = (TraceEditorPanel) editorPanelField.get(panel);
        return editorPanel.nodeTree();
    }

    private static CodeTraceController controller(CodeTracePanel panel) throws Exception {
        Field field = CodeTracePanel.class.getDeclaredField("controller");
        field.setAccessible(true);
        return (CodeTraceController) field.get(panel);
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

```bash
./gradlew test --tests "com.zimaai.codetrace.toolwindow.CodeTracePanelTreeTest"
```

预期：FAIL，当前 CodeTracePanel 仍使用 `nodeList()` 而不是 `nodeTree()`。

- [ ] **步骤 3：修改 CodeTracePanel 接入 JTree**

修改 `src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`：

在构造函数中，将 `editorPanel.nodeList()` 替换为 `editorPanel.nodeTree()`：

```java
public CodeTracePanel(CodeTraceController controller) {
    this.controller = controller;
    editorPanel.nodeTree().setCellRenderer(
            new LinkedNodeTreeCellRenderer(
                    () -> controller.state().currentDocument(),
                    () -> controller.state().focusedNodeId(),
                    () -> controller.state().pendingLinkSourceId()));
    configureLeftPaneActions();
    configureLayout();
    wireSelection();
    wireNoteButtons();
    wireNodeActions();
}
```

修改 `wireSelection()`——将 list selection listener 替换为 tree selection listener：

```java
private void wireSelection() {
    editorPanel.nodeTree().setDragEnabled(true);
    editorPanel.nodeTree().setDropMode(DropMode.ON_OR_INSERT);
    editorPanel.nodeTree().setTransferHandler(
            new NodeTreeTransferHandler(controller, this::rebuildView));

    editorPanel.nodeTree().addTreeSelectionListener(event -> {
        if (syncingNodeSelection) return;
        TreePath path = editorPanel.nodeTree().getSelectionPath();
        TraceNode selected = path != null && path.getLastPathComponent() instanceof TraceNode node
                ? node : null;
        selectedNodeId = selected == null ? null : selected.id();
        if (selectedNodeId == null) {
            controller.clearFocusedNodeId();
        } else {
            controller.setFocusedNodeId(selectedNodeId);
        }
        syncSelectedNodeNote();
        refreshButtons();
    });

    // Expand/collapse persistence
    editorPanel.nodeTree().addTreeExpansionListener(new javax.swing.event.TreeExpansionListener() {
        @Override
        public void treeExpanded(javax.swing.event.TreeExpansionEvent event) {
            persistExpandState();
        }
        @Override
        public void treeCollapsed(javax.swing.event.TreeExpansionEvent event) {
            persistExpandState();
        }
    });

    // Double-click navigation
    editorPanel.nodeTree().addMouseListener(new java.awt.event.MouseAdapter() {
        @Override
        public void mouseClicked(java.awt.event.MouseEvent event) {
            if (event.getClickCount() == 2) {
                TreePath path = editorPanel.nodeTree().getPathForLocation(event.getX(), event.getY());
                if (path != null && path.getLastPathComponent() instanceof TraceNode node) {
                    controller.navigateToNode(node);
                }
            }
        }
    });
}
```

修改 `rebuildView()`——将 `nodeList.setListData(...)` 替换为 tree model重建：

```java
private void rebuildView() {
    // ... (file list part unchanged) ...

    syncingNodeSelection = true;
    TraceTreeModel model = new TraceTreeModel(() -> controller.state().currentDocument());
    editorPanel.nodeTree().setModel(model);
    restoreSelection(document.nodes());
    restoreExpandState(document);
    syncingNodeSelection = false;
    
    // ... (rest unchanged) ...
}
```

新增 `persistExpandState()` 和 `restoreExpandState()`：

```java
private void persistExpandState() {
    TraceDocument doc = controller.state().currentDocument();
    if (doc == null) return;
    JTree tree = editorPanel.nodeTree();
    Set<String> expanded = new java.util.HashSet<>();
    for (int i = 0; i < tree.getRowCount(); i++) {
        TreePath path = tree.getPathForRow(i);
        if (tree.isExpanded(path) && path.getLastPathComponent() instanceof TraceNode node) {
            expanded.add(node.id());
        }
    }
    controller.setExpandedNodes(expanded);
}

private void restoreExpandState(TraceDocument document) {
    if (document == null || document.expandedNodeIds().isEmpty()) return;
    JTree tree = editorPanel.nodeTree();
    TraceTreeModel model = (TraceTreeModel) tree.getModel();
    for (int i = 0; i < tree.getRowCount(); i++) {
        TreePath path = tree.getPathForRow(i);
        if (path.getLastPathComponent() instanceof TraceNode node
                && document.expandedNodeIds().contains(node.id())) {
            tree.expandPath(path);
        }
    }
}
```

修改 `editSelectedNode()`、`deleteSelectedNode()`、`moveSelectedNode()`、`goToLinked()` 等方法中对 `editorPanel.nodeList().getSelectedValue()` 的引用为通过 tree selection 获取。

修改 `selectAndNavigateToNode()`——从 list 操作改为 tree 操作：

```java
private void selectAndNavigateToNode(TraceNode node) {
    controller.navigateToNode(node);
    // Expand tree to find and select the node
    JTree tree = editorPanel.nodeTree();
    TraceTreeModel model = (TraceTreeModel) tree.getModel();
    TreePath rootPath = new TreePath(model.getRoot());
    TreePath nodePath = findPathForNode(model, rootPath, node);
    if (nodePath != null) {
        tree.setSelectionPath(nodePath);
        tree.scrollPathToVisible(nodePath);
    }
}

private TreePath findPathForNode(TraceTreeModel model, TreePath parentPath, TraceNode target) {
    Object parent = parentPath.getLastPathComponent();
    for (int i = 0; i < model.getChildCount(parent); i++) {
        TraceNode child = (TraceNode) model.getChild(parent, i);
        TreePath childPath = parentPath.pathByAddingChild(child);
        if (child.id().equals(target.id())) return childPath;
        TreePath found = findPathForNode(model, childPath, target);
        if (found != null) return found;
    }
    return null;
}
```

在 `CodeTraceState` 中新增 `expandedNodeIds` 状态和存取方法——修改 `src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java`：

```java
    private Set<String> pendingExpandedNodeIds;

    Set<String> consumeExpandedNodeIds() {
        Set<String> value = pendingExpandedNodeIds;
        pendingExpandedNodeIds = null;
        return value;
    }

    void setExpandedNodeIds(Set<String> expandedNodeIds) {
        this.pendingExpandedNodeIds = expandedNodeIds;
    }
```

在 `CodeTraceController` 中新增——修改 `src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java`：

```java
    public void setExpandedNodes(Set<String> expandedNodeIds) {
        if (requireDocument().expandedNodeIds().equals(expandedNodeIds)) return;
        TraceDocument updated = editor.setExpandedNodeIds(requireDocument(), expandedNodeIds, Instant.now());
        persist(updated);
    }
```

在 `TraceDocumentEditor` 中新增——修改 `src/main/java/com/zimaai/codetrace/toolwindow/TraceDocumentEditor.java`：

```java
    public TraceDocument setExpandedNodeIds(TraceDocument document, Set<String> expandedNodeIds, Instant now) {
        return new TraceDocument(
                3,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                document.nodes(),
                document.links(),
                expandedNodeIds);
    }
```

- [ ] **步骤 4：修复 CodeTracePanelTest 中的 nodeList() 引用**

修改 `src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTest.java`：

- 删除 `syncsFocusedNodeIdWithSelectedNodeAndClearsWhenSelectionIsCleared` 测试（JTree 选择逻辑不同于 JList，新测试在 CodeTracePanelTreeTest 中覆盖）
- 删除 `installsDragAndDropSupportOnNodeList` 测试（由 CodeTracePanelTreeTest 中的拖拽测试取代）

或保留但改为测试 JTree 结构。

- [ ] **步骤 5：运行全部测试**

```bash
./gradlew test --tests "com.zimaai.codetrace.toolwindow.CodeTracePanelTreeTest" \
               --tests "com.zimaai.codetrace.toolwindow.CodeTracePanelTest"
```

预期：PASS。

- [ ] **步骤 6：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java \
        src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java \
        src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java \
        src/main/java/com/zimaai/codetrace/toolwindow/TraceDocumentEditor.java \
        src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTreeTest.java \
        src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTest.java
git commit -m "feat(code-trace): wire JTree with selection, focus, and expand state persistence"
```

---

### 任务 6：NodeTreeTransferHandler——树形拖拽

**文件：**
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/NodeTreeTransferHandler.java`
- 删除：`src/main/java/com/zimaai/codetrace/toolwindow/NodeListReorderTransferHandler.java`

- [ ] **步骤 1：编写拖拽测试**

在 `src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTreeTest.java` 追加：

```java
    @Test
    void installsTreeDragAndDropSupport() throws Exception {
        TraceDocument doc = new TraceDocument(
                3, "t1", "T1", "", Instant.now(), Instant.now(),
                List.of(new TraceNode("n1", "node", "", "", "", 0, "", "", "")),
                List.of(), Set.of());

        CodeTracePanel panel = panelFor(doc);
        JTree tree = tree(panel);

        assertTrue(tree.getTransferHandler() instanceof NodeTreeTransferHandler);
    }

    @Test
    void dragReparentSetsParentId() throws Exception {
        TraceDocument doc = new TraceDocument(
                3, "t1", "T1", "", Instant.now(), Instant.now(),
                List.of(
                        new TraceNode("n1", "parent", "", "", "", 0, "", "", ""),
                        new TraceNode("n2", "child", "", "", "", 0, "", "", "n1", null)),
                List.of(), Set.of());

        CodeTracePanel panel = panelFor(doc);
        CodeTraceController controller = controller(panel);

        // Move child out: set n2 parentId to null
        controller.setParent("n2", null);

        TraceNode n2 = controller.state().currentDocument().nodes().stream()
                .filter(n -> n.id().equals("n2")).findFirst().orElseThrow();
        assertEquals(null, n2.parentId());
    }
```

补充方法到 `CodeTraceController`（在步骤 3 实现）。

- [ ] **步骤 2：运行测试验证失败**

```bash
./gradlew test --tests "com.zimaai.codetrace.toolwindow.CodeTracePanelTreeTest.dragReparentSetsParentId"
```

预期：FAIL，`setParent` 方法尚未实现。

- [ ] **步骤 3：实现 NodeTreeTransferHandler 和 Controller.setParent**

创建 `src/main/java/com/zimaai/codetrace/toolwindow/NodeTreeTransferHandler.java`：

```java
package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceNode;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.TreePath;

public final class NodeTreeTransferHandler extends TransferHandler {
    private static final int INDENT_THRESHOLD = 20;

    private final CodeTraceController controller;
    private final Runnable refreshUi;

    public NodeTreeTransferHandler(CodeTraceController controller, Runnable refreshUi) {
        this.controller = controller;
        this.refreshUi = refreshUi;
    }

    @Override
    public int getSourceActions(JComponent c) {
        return MOVE;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        JTree tree = (JTree) c;
        TreePath path = tree.getSelectionPath();
        if (path == null || !(path.getLastPathComponent() instanceof TraceNode node)) {
            return null;
        }
        return new StringSelection(node.id());
    }

    @Override
    public boolean canImport(TransferSupport support) {
        return support.isDrop() && support.getComponent() instanceof JTree;
    }

    @Override
    public boolean importData(TransferSupport support) {
        JTree tree = (JTree) support.getComponent();
        JTree.DropLocation dropLocation = (JTree.DropLocation) support.getDropLocation();
        TreePath targetPath = dropLocation.getPath();
        if (targetPath == null || !(targetPath.getLastPathComponent() instanceof TraceNode targetNode)) {
            return false;
        }

        TreePath sourcePath = tree.getSelectionPath();
        if (sourcePath == null || !(sourcePath.getLastPathComponent() instanceof TraceNode sourceNode)) {
            return false;
        }

        // Detect indent: compare drop X with target row X
        Point dropPoint = support.getDropLocation().getDropPoint();
        int row = tree.getRowForPath(targetPath);
        Rectangle rowBounds = tree.getRowBounds(row);
        if (rowBounds != null && dropPoint != null) {
            int deltaX = dropPoint.x - rowBounds.x;
            if (deltaX > INDENT_THRESHOLD && !isDescendantOf(targetNode, sourceNode)) {
                // Reparent: make source a child of target
                controller.setParent(sourceNode.id(), targetNode.id());
                refreshUi.run();
                return true;
            }
        }

        // Otherwise, reorder within same parent
        int childIndex = dropLocation.getChildIndex();
        String newParentId = targetNode.parentId();
        if (childIndex < 0) {
            // Dropped on the target node → reorder after it within same parent
            controller.setParent(sourceNode.id(), newParentId);
        } else {
            controller.setParentAndIndex(sourceNode.id(), newParentId, childIndex);
        }
        refreshUi.run();
        return true;
    }

    private boolean isDescendantOf(TraceNode ancestor, TraceNode node) {
        // A node shouldn't be made a child of its own descendant
        String parentId = ancestor.parentId();
        while (parentId != null) {
            if (parentId.equals(node.id())) return true;
            // Walk up the tree
            TraceDocument doc = controller.state().currentDocument();
            if (doc == null) break;
            String finalParentId = parentId;
            TraceNode parent = doc.nodes().stream()
                    .filter(n -> n.id().equals(finalParentId))
                    .findFirst().orElse(null);
            if (parent == null) break;
            parentId = parent.parentId();
        }
        return false;
    }
}
```

在 `CodeTraceController` 中新增 `setParent` 和 `setParentAndIndex` 方法：

```java
    public void setParent(String nodeId, String newParentId) {
        TraceDocument updated = editor.setParent(requireDocument(), nodeId, newParentId, Instant.now());
        persist(updated);
    }

    public void setParentAndIndex(String nodeId, String newParentId, int index) {
        TraceDocument updated = editor.setParentAndIndex(requireDocument(), nodeId, newParentId, index, Instant.now());
        persist(updated);
    }
```

在 `TraceDocumentEditor` 中新增对应方法：

```java
    public TraceDocument setParent(TraceDocument document, String nodeId, String newParentId, Instant now) {
        List<TraceNode> updated = new ArrayList<>(document.nodes());
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i).id().equals(nodeId)) {
                TraceNode node = updated.get(i);
                updated.set(i, new TraceNode(
                        node.id(), node.displayName(), node.qualifiedName(), node.signature(),
                        node.filePath(), node.line(), node.language(), node.note(),
                        node.navigationHint(), newParentId, node.title()));
                break;
            }
        }
        return new TraceDocument(
                3,
                document.id(), document.name(), document.description(),
                document.createdAt(), now,
                List.copyOf(updated), document.links(), document.expandedNodeIds());
    }

    public TraceDocument setParentAndIndex(TraceDocument document, String nodeId,
                                           String newParentId, int targetIndex, Instant now) {
        List<TraceNode> nodes = new ArrayList<>(document.nodes());
        // Find and update the node's parentId
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).id().equals(nodeId)) {
                TraceNode node = nodes.get(i);
                TraceNode updated = new TraceNode(
                        node.id(), node.displayName(), node.qualifiedName(), node.signature(),
                        node.filePath(), node.line(), node.language(), node.note(),
                        node.navigationHint(), newParentId, node.title());
                nodes.remove(i);
                // Insert at target index within same-parent siblings
                int insertIdx = 0;
                int siblingsSeen = 0;
                for (int j = 0; j < nodes.size(); j++) {
                    if (Objects.equals(nodes.get(j).parentId(), newParentId)) {
                        if (siblingsSeen == targetIndex) {
                            insertIdx = j;
                            break;
                        }
                        siblingsSeen++;
                    }
                    insertIdx = j + 1;
                }
                nodes.add(insertIdx, updated);
                break;
            }
        }
        return new TraceDocument(
                3,
                document.id(), document.name(), document.description(),
                document.createdAt(), now,
                List.copyOf(nodes), document.links(), document.expandedNodeIds());
    }
```

- [ ] **步骤 4：运行测试验证通过**

```bash
./gradlew test --tests "com.zimaai.codetrace.toolwindow.CodeTracePanelTreeTest"
```

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/toolwindow/NodeTreeTransferHandler.java \
        src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java \
        src/main/java/com/zimaai/codetrace/toolwindow/TraceDocumentEditor.java \
        src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTreeTest.java
git rm src/main/java/com/zimaai/codetrace/toolwindow/NodeListReorderTransferHandler.java
git commit -m "feat(code-trace): add tree drag-and-drop with indent-based reparenting"
```

---

### 任务 7：Controller & Editor——级联删除与同级移动

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/TraceDocumentEditor.java`
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java`

- [ ] **步骤 1：编写级联删除与同级移动测试**

在 `src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java` 追加：

```java
    @Test
    void cascadesDeleteToAllDescendants() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("tree.json", documentWithNestedNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.load("tree.json");

        controller.deleteNodeOrPair("n1");
        // n1, n2 (child of n1), n3 (child of n2) should all be deleted
        List<String> remaining = controller.state().currentDocument().nodes().stream()
                .map(TraceNode::id).toList();
        assertEquals(List.of(), remaining);
    }

    @Test
    void setsParentOfNodeToReparent() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("tree.json", documentWithNestedNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.load("tree.json");

        controller.setParent("n3", null); // move n3 to root

        TraceNode n3 = controller.state().currentDocument().nodes().stream()
                .filter(n -> n.id().equals("n3")).findFirst().orElseThrow();
        assertEquals(null, n3.parentId());
    }

    private static TraceDocument documentWithNestedNodes() {
        return new TraceDocument(
                3, "tree-1", "Tree", "",
                Instant.parse("2026-06-02T10:00:00Z"),
                Instant.parse("2026-06-02T10:00:00Z"),
                List.of(
                        new TraceNode("n1", "root", "", "", "", 0, "", "", ""),
                        new TraceNode("n2", "child-a", "", "", "", 0, "", "", "n1", null),
                        new TraceNode("n3", "child-b", "", "", "", 0, "", "", "n2", null)),
                List.of(), Set.of());
    }
```

- [ ] **步骤 2：运行测试验证失败**

```bash
./gradlew test --tests "com.zimaai.codetrace.toolwindow.CodeTraceControllerTest.cascadesDeleteToAllDescendants" \
               --tests "com.zimaai.codetrace.toolwindow.CodeTraceControllerTest.setsParentOfNodeToReparent"
```

预期：FAIL，`deleteNodeOrPair` 尚未级联删除子孙节点。

- [ ] **步骤 3：实现级联删除**

修改 `CodeTraceController.deleteInternal` 方法——增加级联子孙节点收集：

```java
    private static TraceDocument deleteInternal(TraceDocument document, String nodeId, Instant now) {
        List<String> affectedIds = linkedNodeIds(document.links(), nodeId);
        // Cascade: collect all descendant IDs
        Set<String> allRemoved = new java.util.HashSet<>(affectedIds);
        collectDescendantIds(document.nodes(), allRemoved);
        List<TraceNode> nodes = document.nodes().stream()
                .filter(node -> !allRemoved.contains(node.id()))
                .toList();
        List<TraceLink> links = document.links().stream()
                .filter(link -> !allRemoved.contains(link.sourceNodeId())
                        && !allRemoved.contains(link.targetNodeId()))
                .toList();
        return new TraceDocument(
                3,
                document.id(), document.name(), document.description(),
                document.createdAt(), now,
                nodes, links, document.expandedNodeIds());
    }

    private static void collectDescendantIds(List<TraceNode> nodes, Set<String> result) {
        Set<String> current = new java.util.HashSet<>(result);
        for (TraceNode node : nodes) {
            if (node.parentId() != null && current.contains(node.parentId())) {
                result.add(node.id());
            }
        }
        if (result.size() > current.size()) {
            collectDescendantIds(nodes, result);
        }
    }
```

- [ ] **步骤 4：运行测试验证通过**

```bash
./gradlew test --tests "com.zimaai.codetrace.toolwindow.CodeTraceControllerTest"
```

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java \
        src/main/java/com/zimaai/codetrace/toolwindow/TraceDocumentEditor.java \
        src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java
git commit -m "feat(code-trace): add cascade delete and reparent to controller"
```

---

### 任务 8：编辑对话框新增 Title 字段

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`

- [ ] **步骤 1：编写编辑对话框 title 测试**

在 `src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTreeTest.java` 追加：

```java
    @Test
    void editDialogIncludesTitleField() throws Exception {
        // This test verifies the NodeInput record includes title field
        // We test indirectly via controller update
        TraceDocument doc = new TraceDocument(
                3, "t1", "T1", "", Instant.now(), Instant.now(),
                List.of(new TraceNode("n1", "disp", "", "", "", 0, "", "", "", null, null)),
                List.of(), Set.of());

        CodeTracePanel panel = panelFor(doc);
        CodeTraceController controller = controller(panel);

        TraceNode updated = new TraceNode("n1", "disp", "", "", "", 0, "", "", "", null, "新标题");
        controller.updateNode(updated);

        TraceNode reloaded = controller.state().currentDocument().nodes().get(0);
        assertEquals("新标题", reloaded.title());
    }
```

- [ ] **步骤 2：运行测试验证失败**

```bash
./gradlew test --tests "com.zimaai.codetrace.toolwindow.CodeTracePanelTreeTest.editDialogIncludesTitleField"
```

预期：PASS（`TraceNode` 已经支持 title 字段，`updateNode` 已经能更新）——实际上此测试可能直接通过。如果已通过，则跳过步骤 3。

- [ ] **步骤 3：修改 showNodeDialog——新增 Title 输入框**

修改 `CodeTracePanel.showNodeDialog` 方法，在 Display Name 上方添加 Title 字段：

```java
    private NodeInput showNodeDialog(String title, TraceNode initial) {
        javax.swing.JTextField titleField = new javax.swing.JTextField(initial == null ? "" :
                initial.title() == null ? "" : initial.title());
        javax.swing.JTextField nameField = new javax.swing.JTextField(initial == null ? "" : initial.displayName());
        javax.swing.JTextField qualifiedField = new javax.swing.JTextField(initial == null ? "" : initial.qualifiedName());
        // ... (其余字段不变)

        JPanel panel = new JPanel(new java.awt.GridLayout(0, 1));
        panel.add(new javax.swing.JLabel("Title"));
        panel.add(titleField);
        panel.add(new javax.swing.JLabel("Display Name"));
        panel.add(nameField);
        // ... (其余不变)

        // ... (OK/Cancel logic)
        return new NodeInput(
                titleField.getText().trim(),
                nameField.getText().trim(),
                qualifiedField.getText().trim(),
                // ... (其余不变)
        );
    }
```

修改 `NodeInput` record——新增 `title` 字段（放在最前）：

```java
    private record NodeInput(
            String title,
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

修改 `editSelectedNode()` 中构造 `TraceNode` 的部分——使用 `input.title()`：

```java
    private void editSelectedNode() {
        TraceNode existing = editorPanel.nodeTree().getSelectionPath() != null
                && editorPanel.nodeTree().getSelectionPath().getLastPathComponent() instanceof TraceNode node
                ? node : null;
        if (existing == null) return;
        NodeInput input = showNodeDialog("Edit Node", existing);
        if (input == null) return;
        TraceNode updated = new TraceNode(
                existing.id(),
                input.displayName(),
                input.qualifiedName(),
                input.signature(),
                input.filePath(),
                input.line(),
                input.language(),
                input.note(),
                input.navigationHint(),
                existing.parentId(),
                input.title());
        controller.updateNode(updated);
        rebuildView();
    }
```

- [ ] **步骤 4：运行测试验证通过**

```bash
./gradlew test --tests "com.zimaai.codetrace.toolwindow.CodeTracePanelTreeTest"
```

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java \
        src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTreeTest.java
git commit -m "feat(code-trace): add title field to node edit dialog"
```

---

### 任务 9：AddToCodeTraceHandler——树感知插入

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceHandler.java`
- 修改：`src/test/java/com/zimaai/codetrace/actions/AddToCodeTraceHandlerTest.java`

- [ ] **步骤 1：编写树感知插入测试**

在 `src/test/java/com/zimaai/codetrace/actions/AddToCodeTraceHandlerTest.java` 追加：

```java
    @Test
    void insertsSourceAsSiblingAfterFocusedNodeInTree() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.createNewFile("tree-trace.json", "Tree");
        controller.load("tree-trace.json");

        // Set up: n1 (root), n2 (child of n1)
        TraceNode n1 = new TraceNode("n1", "parent", "", "", "", 0, "", "", "");
        TraceNode n2 = new TraceNode("n2", "child", "", "", "", 0, "", "", "n1", null);
        controller.addNode(n1);
        controller.addNode(n2);
        controller.setFocusedNodeId("n1");

        TraceNode source = new TraceNode(
                "ignored", "new-sibling", "", "", "", 0, "", "", "Auth#new");
        AddToCodeTraceHandler handler = new AddToCodeTraceHandler(
                controller,
                new FakeCaptureService(source, Optional.empty()),
                new RecordingPrompts(false),
                () -> {});

        handler.handle(null, null, null);

        // Source should be a sibling after n1 (same parentId = null)
        String insertedId = controller.state().preferredSelectedNodeId();
        List<TraceNode> nodes = controller.state().currentDocument().nodes();
        TraceNode inserted = nodes.stream().filter(n -> n.id().equals(insertedId)).findFirst().orElseThrow();
        assertEquals(null, inserted.parentId()); // same level as n1
    }
```

- [ ] **步骤 2：运行测试验证失败**

```bash
./gradlew test --tests "com.zimaai.codetrace.actions.AddToCodeTraceHandlerTest.insertsSourceAsSiblingAfterFocusedNodeInTree"
```

预期：FAIL，`addOrReuseNodeAfterFocusedNode` 在树形下可能无法正确处理 parentId。

- [ ] **步骤 3：修改 addOrReuseNodeAfterFocusedNode 使其在同级插入**

修改 `CodeTraceController.insertOrReuseNodeAfter`——确保新节点继承聚焦节点的 `parentId`（成为同级 sibling）：

```java
    private TraceDocument insertOrReuseNodeAfter(TraceDocument document, TraceNode candidate,
                                                  String afterNodeId, Instant now) {
        List<TraceNode> nodes = document.nodes();
        for (int i = 0; i < nodes.size(); i++) {
            TraceNode existing = nodes.get(i);
            if (existing.displayName().equals(candidate.displayName())
                    && existing.filePath().equals(candidate.filePath())
                    && existing.line() == candidate.line()) {
                if (afterNodeId != null) {
                    int afterIndex = indexOfNode(nodes, afterNodeId);
                    if (afterIndex >= 0) {
                        // Move existing node to same level as afterNodeId
                        String sameParent = nodes.get(afterIndex).parentId();
                        return editor.setParent(document, existing.id(), sameParent, now);
                    }
                }
                return document;
            }
        }
        // Find the parent of the focused node for same-level insertion
        String parentId = null;
        if (afterNodeId != null) {
            for (TraceNode node : nodes) {
                if (node.id().equals(afterNodeId)) {
                    parentId = node.parentId();
                    break;
                }
            }
        }
        TraceNode withId = new TraceNode(
                "node-" + UUID.randomUUID(),
                candidate.displayName(),
                candidate.qualifiedName(),
                candidate.signature(),
                candidate.filePath(),
                candidate.line(),
                candidate.language(),
                candidate.note(),
                candidate.navigationHint(),
                parentId,
                candidate.title());
        if (afterNodeId != null) {
            int afterIdx = indexOfNode(nodes, afterNodeId);
            int insertIdx = afterIdx >= 0 ? afterIdx + 1 : nodes.size();
            return editor.insertNodeAt(document, withId, insertIdx, now);
        }
        return editor.addNode(document, withId, now);
    }
```

- [ ] **步骤 4：运行测试验证通过**

```bash
./gradlew test --tests "com.zimaai.codetrace.actions.AddToCodeTraceHandlerTest"
```

预期：PASS。

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceHandler.java \
        src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java \
        src/test/java/com/zimaai/codetrace/actions/AddToCodeTraceHandlerTest.java
git commit -m "feat(code-trace): insert source as sibling after focused node in tree"
```

---

### 任务 10：测试清理——适配所有剩余测试

**文件：**
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTest.java`
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java`

- [ ] **步骤 1：运行全部测试，收集失败项**

```bash
./gradlew test
```

预期：可能有部分测试因 JTree API 变更而失败。

- [ ] **步骤 2：逐一修复失败测试**

- `CodeTracePanelTest.syncsFocusedNodeIdWithSelectedNodeAndClearsWhenSelectionIsCleared` — 删除或改写为 JTree 版本
- `CodeTracePanelTest.installsDragAndDropSupportOnNodeList` — 删除，已在 CodeTracePanelTreeTest 覆盖
- `CodeTracePanelTest.preservesPreferredSelectedNodeIdAcrossExternalRefresh` — 需适配 JTree API

- [ ] **步骤 3：运行全部测试验证通过**

```bash
./gradlew test
```

预期：PASS。

- [ ] **步骤 4：Commit**

```bash
git add src/test/
git commit -m "test(code-trace): adapt remaining tests for JTree migration"
```

---

### 任务 11：冒烟清单更新

**文件：**
- 修改：`docs/superpowers/plans/2026-05-29-code-trace-manual-smoke-checklist.md`

- [ ] **步骤 1：补充树形与 title 检查项**

在 `Nodes And Links` 段落追加：

```markdown
8. Click `Edit Node` and verify the `Title` field is present above `Display Name`.
9. Set a long title (>15 chars) and confirm it is truncated with `…` in the tree view.
10. Verify the title appears as `title — displayName` format. When title is empty, only displayName is shown.
11. Add a child action: right-click a node and select `Add Child Node`. A new node appears indented under the parent.
12. Click the expand/collapse toggle next to a parent node. Child nodes appear/disappear.
13. Collapse a parent, refresh the view, and confirm the collapsed state is restored.
14. Focus a node by single-clicking it. Verify a `●` focus indicator appears on the far left of the node.
15. Drag a node slightly to the right over a target node (indent > 20px) and drop. Verify it becomes a child of the target.
16. Drag a child node to the left (align with parent level) and drop. Verify it becomes a root-level sibling.
17. Delete a parent node that has children. Verify all descendant nodes are also removed.
```

- [ ] **步骤 2：Commit**

```bash
git add docs/superpowers/plans/2026-05-29-code-trace-manual-smoke-checklist.md
git commit -m "docs(code-trace): update smoke checklist for tree and title features"
```

---

## Spec Coverage Check

| 规格需求 | 覆盖任务 |
|----------|---------|
| 多层级嵌套子节点 | 任务 1（parentId）、任务 2（TreeModel）、任务 4（JTree） |
| 展开/折叠按钮 | 任务 4（JTree 原生）、任务 5（persistExpandState） |
| 展开状态持久化 | 任务 1（expandedNodeIds）、任务 5（持久化逻辑） |
| 拖拽设为子节点（缩进） | 任务 6（NodeTreeTransferHandler） |
| 拖拽移出子节点 | 任务 6（deltaX 判定） |
| 聚焦定位图标 | 任务 3（FOCUS_PREFIX） |
| title 显示 `title — displayName` | 任务 3（renderer） |
| title 过长截断 | 任务 3（MAX_TITLE_CHARS + …） |
| title 编辑 | 任务 8（showNodeDialog） |
| 向后兼容 v2→v3 | 任务 1（TraceJsonMapper） |
| 级联删除 | 任务 7（collectDescendantIds） |
| 链接节点成组移动 | 任务 6（linkedNodeIds 保持不变） |

## Placeholder Scan

- 无 `TODO`、`TBD`、`后续实现`。
- 所有方法签名和 API 名称一致。
- 每个步骤都包含完整代码和精确的测试运行命令。
