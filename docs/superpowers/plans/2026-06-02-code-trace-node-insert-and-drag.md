# code-trace 节点插入与拖拽实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 让 `Add to code-trace` 把 source 节点插到当前聚焦节点下方并自动聚焦新节点，同时支持在节点列表中直接拖拽重排，拖拽时继续保持“链接节点成组移动”的规则。

**架构：** `CodeTraceState` 增加一个当前聚焦节点 ID，`CodeTracePanel` 负责在列表选择变化时同步这个状态，外部右键动作则通过控制器读取它来决定插入点。`CodeTraceController` 提供“按指定位置插入节点”和“按目标索引移动节点/节点组”两类能力，`TraceDocumentEditor` 继续只负责纯文档增删改。节点列表拖拽通过一个专用 transfer handler 转成控制器调用，避免把排序算法散落在 UI 事件里。

**技术栈：** Java 21、Swing、IntelliJ Platform SDK、JUnit 5

---

## Planned File Structure

### Focus state and insertion

- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/TraceDocumentEditor.java`
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java`

### Selection sync and drag reorder

- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/NodeListReorderTransferHandler.java`
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTest.java`

### Add action integration

- 修改：`src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceHandler.java`
- 修改：`src/test/java/com/zimaai/codetrace/actions/AddToCodeTraceHandlerTest.java`

### Manual smoke checklist

- 修改：`docs/superpowers/plans/2026-05-29-code-trace-manual-smoke-checklist.md`

## Task 1: 让控制器支持“聚焦后插入”和“按目标索引移动”

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/TraceDocumentEditor.java`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java`
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java`

- [ ] **步骤 1：编写失败的控制器测试**

在 `src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java` 追加：

```java
    @Test
    void insertsSourceAfterFocusedNodeAndAppendsWithoutFocus() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-1.json", documentWithThreeNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.load("trace-1.json");

        TraceNode source = new TraceNode(
                "temp-source",
                "source",
                "S#source",
                "source()",
                "src/S.java",
                40,
                "JAVA",
                "",
                "S#source");
        controller.setFocusedNodeId("node-2");
        int sourceIndex = controller.addOrReuseNodeAfterFocusedNode(source);
        String sourceId = controller.state().currentDocument().nodes().get(sourceIndex).id();

        assertEquals(List.of("node-1", "node-2", sourceId, "node-3"),
                controller.state().currentDocument().nodes().stream().map(TraceNode::id).toList());

        controller.clearFocusedNodeId();
        TraceNode tail = new TraceNode(
                "temp-tail",
                "tail",
                "T#tail",
                "tail()",
                "src/T.java",
                50,
                "JAVA",
                "",
                "T#tail");
        int tailIndex = controller.addOrReuseNodeAfterFocusedNode(tail);
        String tailId = controller.state().currentDocument().nodes().get(tailIndex).id();

        assertEquals(List.of("node-1", "node-2", sourceId, "node-3", tailId),
                controller.state().currentDocument().nodes().stream().map(TraceNode::id).toList());
    }

    @Test
    void reusesExistingSourceNodeAndMovesItAfterFocus() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-1.json", documentWithThreeNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.load("trace-1.json");

        TraceNode existing = controller.state().currentDocument().nodes().get(0);
        controller.setFocusedNodeId("node-2");
        int sourceIndex = controller.addOrReuseNodeAfterFocusedNode(existing);

        assertEquals(1, sourceIndex);
        assertEquals(List.of("node-2", "node-1", "node-3"),
                controller.state().currentDocument().nodes().stream().map(TraceNode::id).toList());
    }

    @Test
    void movesLinkedNodeGroupToExactIndexForDragReorder() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-1.json", linkedDocumentWithThreeNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.load("trace-1.json");

        controller.moveNodeOrPairToIndex("node-2", 0);

        assertEquals(List.of("node-2", "node-3", "node-1"),
                controller.state().currentDocument().nodes().stream().map(TraceNode::id).toList());
    }
```

补充导入：

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```powershell
.\gradlew.bat test --tests "com.zimaai.codetrace.toolwindow.CodeTraceControllerTest"
```

预期：FAIL，编译错误会指向 `setFocusedNodeId(...)`、`clearFocusedNodeId()`、`addOrReuseNodeAfterFocusedNode(...)`、`moveNodeOrPairToIndex(...)` 尚未实现。

- [ ] **步骤 3：编写最少实现代码**

把 `src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java` 改成：

```java
package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;

public final class CodeTraceState {
    private String currentFileName;
    private TraceDocument currentDocument;
    private String pendingLinkSourceId;
    private String preferredSelectedNodeId;
    private String focusedNodeId;

    public String currentFileName() {
        return currentFileName;
    }

    public TraceDocument currentDocument() {
        return currentDocument;
    }

    public String pendingLinkSourceId() {
        return pendingLinkSourceId;
    }

    public String preferredSelectedNodeId() {
        return preferredSelectedNodeId;
    }

    public String focusedNodeId() {
        return focusedNodeId;
    }

    void load(String fileName, TraceDocument document) {
        this.currentFileName = fileName;
        this.currentDocument = document;
        this.pendingLinkSourceId = null;
        this.preferredSelectedNodeId = null;
        this.focusedNodeId = null;
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

    void setPreferredSelectedNodeId(String preferredSelectedNodeId) {
        this.preferredSelectedNodeId = preferredSelectedNodeId;
    }

    String consumePreferredSelectedNodeId() {
        String preferred = preferredSelectedNodeId;
        preferredSelectedNodeId = null;
        return preferred;
    }

    void setFocusedNodeId(String focusedNodeId) {
        this.focusedNodeId = focusedNodeId;
    }

    void clearFocusedNodeId() {
        this.focusedNodeId = null;
    }
}
```

把 `src/main/java/com/zimaai/codetrace/toolwindow/TraceDocumentEditor.java` 追加：

```java
    public TraceDocument insertNodeAt(TraceDocument document, TraceNode node, int index, Instant now) {
        List<TraceNode> nodes = new ArrayList<>(document.nodes());
        int boundedIndex = Math.max(0, Math.min(index, nodes.size()));
        nodes.add(boundedIndex, node);
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
```

把 `src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java` 追加和调整成：

```java
    public void setFocusedNodeId(String nodeId) {
        state.setFocusedNodeId(nodeId);
    }

    public void clearFocusedNodeId() {
        state.clearFocusedNodeId();
    }

    public String focusedNodeId() {
        return state.focusedNodeId();
    }

    public int addOrReuseNodeAfterFocusedNode(TraceNode candidate) {
        String afterNodeId = state.focusedNodeId();
        TraceDocument updated = insertOrReuseNodeAfter(requireDocument(), candidate, afterNodeId, Instant.now());
        persist(updated);
        return indexOfNode(updated.nodes(), resolveInsertedNodeId(updated, candidate));
    }

    public void moveNodeOrPairToIndex(String nodeId, int targetIndex) {
        TraceDocument updated = moveInternalToIndex(requireDocument(), nodeId, targetIndex, Instant.now());
        persist(updated);
    }
```

`addNode(...)` 继续保留给“直接追加”场景，但内部改成走 `TraceDocumentEditor.insertNodeAt(...)`，`insertOrReuseNodeAfter(...)` 和 `moveInternalToIndex(...)` 负责复用现有 `linkedNodeIds(...)` 与 `indexOfNode(...)` 逻辑。

- [ ] **步骤 4：运行测试验证通过**

运行：

```powershell
.\gradlew.bat test --tests "com.zimaai.codetrace.toolwindow.CodeTraceControllerTest"
```

预期：PASS。

- [ ] **步骤 5：Commit**

```powershell
git add src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java src/main/java/com/zimaai/codetrace/toolwindow/TraceDocumentEditor.java src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java
git commit -m "feat(code-trace): support focused insertion and drag moves"
```

## Task 2: 让面板同步当前聚焦节点，并接入拖拽重排

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/NodeListReorderTransferHandler.java`
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTest.java`

- [ ] **步骤 1：编写失败的面板同步与拖拽测试**

在 `src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTest.java` 追加：

```java
    @Test
    void syncsFocusedNodeIdWithSelectedNodeAndClearsWhenSelectionIsCleared() throws Exception {
        CodeTracePanel panel = panelFor(documentWithOneNode());
        CodeTraceController controller = controller(panel);
        TraceEditorPanel editorPanel = editorPanel(panel);

        editorPanel.nodeList().setSelectedIndex(0);
        assertEquals("node-1", controller.focusedNodeId());

        editorPanel.nodeList().clearSelection();
        assertEquals(null, controller.focusedNodeId());
    }

    @Test
    void installsDragAndDropSupportOnNodeList() throws Exception {
        CodeTracePanel panel = panelFor(documentWithOneNode());
        TraceEditorPanel editorPanel = editorPanel(panel);

        assertTrue(editorPanel.nodeList().isDragEnabled());
        assertEquals(DropMode.INSERT, editorPanel.nodeList().getDropMode());
        assertTrue(editorPanel.nodeList().getTransferHandler() instanceof NodeListReorderTransferHandler);
    }
```

补充导入：

```java
import java.awt.dnd.DropMode;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
```

并在测试类底部补两个反射 helper：

```java
    private static CodeTraceController controller(CodeTracePanel panel) throws Exception {
        Field field = CodeTracePanel.class.getDeclaredField("controller");
        field.setAccessible(true);
        return (CodeTraceController) field.get(panel);
    }

    private static TraceEditorPanel editorPanel(CodeTracePanel panel) throws Exception {
        Field field = CodeTracePanel.class.getDeclaredField("editorPanel");
        field.setAccessible(true);
        return (TraceEditorPanel) field.get(panel);
    }
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```powershell
.\gradlew.bat test --tests "com.zimaai.codetrace.toolwindow.CodeTracePanelTest"
```

预期：FAIL，当前面板不会把列表选择同步到 `focusedNodeId`，也不会启用 drag/drop。

- [ ] **步骤 3：编写最少实现代码**

把 `src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java` 的选择同步和列表初始化改成：

```java
        editorPanel.nodeList().setDragEnabled(true);
        editorPanel.nodeList().setDropMode(DropMode.INSERT);
        editorPanel.nodeList().setTransferHandler(new NodeListReorderTransferHandler(controller, this::rebuildView));

        editorPanel.nodeList().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting() || syncingNodeSelection) {
                return;
            }
            TraceNode selected = editorPanel.nodeList().getSelectedValue();
            selectedNodeId = selected == null ? null : selected.id();
            if (selectedNodeId == null) {
                controller.clearFocusedNodeId();
            } else {
                controller.setFocusedNodeId(selectedNodeId);
            }
            syncSelectedNodeNote();
            refreshButtons();
        });
```

把 `restoreSelection(...)` 改成在设置 `selectedNodeId` 后同步 controller：

```java
        if (preferredSelectedNodeId != null) {
            ...
            selectedNodeId = preferredSelectedNodeId;
            controller.setFocusedNodeId(selectedNodeId);
            editorPanel.nodeList().setSelectedIndex(i);
            return;
        }
        ...
        if (selectedNodeId != null) {
            controller.setFocusedNodeId(selectedNodeId);
            editorPanel.nodeList().setSelectedIndex(i);
            return;
        }
        selectedNodeId = null;
        controller.clearFocusedNodeId();
        editorPanel.nodeList().clearSelection();
```

把 `deleteSelectedNode()` 改成先清空聚焦状态再删：

```java
    private void deleteSelectedNode() {
        if (selectedNodeId == null) {
            return;
        }
        String nodeId = selectedNodeId;
        selectedNodeId = null;
        controller.clearFocusedNodeId();
        controller.deleteNodeOrPair(nodeId);
        rebuildView();
    }
```

创建 `src/main/java/com/zimaai/codetrace/toolwindow/NodeListReorderTransferHandler.java`，核心逻辑如下：

```java
public final class NodeListReorderTransferHandler extends TransferHandler {
    private final CodeTraceController controller;
    private final Runnable refreshUi;

    @Override
    public boolean canImport(TransferSupport support) {
        return support.isDrop() && support.getComponent() instanceof JBList<?>;
    }

    @Override
    public boolean importData(TransferSupport support) {
        JList.DropLocation dropLocation = (JList.DropLocation) support.getDropLocation();
        JBList<TraceNode> list = (JBList<TraceNode>) support.getComponent();
        TraceNode selected = list.getSelectedValue();
        if (selected == null) {
            return false;
        }
        controller.moveNodeOrPairToIndex(selected.id(), dropLocation.getIndex());
        refreshUi.run();
        return true;
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：

```powershell
.\gradlew.bat test --tests "com.zimaai.codetrace.toolwindow.CodeTracePanelTest" --tests "com.zimaai.codetrace.toolwindow.CodeTraceControllerTest"
```

预期：PASS。

- [ ] **步骤 5：Commit**

```powershell
git add src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java src/main/java/com/zimaai/codetrace/toolwindow/NodeListReorderTransferHandler.java src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTest.java src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java
git commit -m "feat(code-trace): sync focused node and enable drag reorder"
```

## Task 3: 让 `Add to code-trace` 读取当前聚焦节点并插入到其下方

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceHandler.java`
- 修改：`src/test/java/com/zimaai/codetrace/actions/AddToCodeTraceHandlerTest.java`

- [ ] **步骤 1：编写失败的 handler 测试**

在 `src/test/java/com/zimaai/codetrace/actions/AddToCodeTraceHandlerTest.java` 追加：

```java
    @Test
    void insertsSourceAfterFocusedNodeAndPrefersItAfterRefresh() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.createNewFile("trace-2.json", "Trace 2");
        controller.load("trace-2.json");
        controller.setFocusedNodeId("node-2");

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

        AddToCodeTraceHandler handler = new AddToCodeTraceHandler(
                controller,
                new FakeCaptureService(source, Optional.empty()),
                new RecordingPrompts(false),
                () -> {
                });

        handler.handle(null, null, null);

        assertEquals(List.of("node-1", "node-2", "node-3", controller.state().preferredSelectedNodeId()),
                controller.state().currentDocument().nodes().stream().map(TraceNode::id).toList());
    }

    @Test
    void appendsSourceToBottomWhenNothingIsFocused() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.createNewFile("trace-3.json", "Trace 3");
        controller.load("trace-3.json");

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

        AddToCodeTraceHandler handler = new AddToCodeTraceHandler(
                controller,
                new FakeCaptureService(source, Optional.empty()),
                new RecordingPrompts(false),
                () -> {
                });

        handler.handle(null, null, null);

        assertEquals(4, controller.state().currentDocument().nodes().size());
        assertEquals(controller.state().currentDocument().nodes().get(3).id(), controller.state().preferredSelectedNodeId());
    }
```

把上面的第一个断言里的 `List.of(...)` 改成更稳妥的写法时，可先取出插入后的 source id：

```java
        String insertedId = controller.state().preferredSelectedNodeId();
        assertEquals(List.of("node-1", "node-2", insertedId, "node-3"),
                controller.state().currentDocument().nodes().stream().map(TraceNode::id).toList());
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```powershell
.\gradlew.bat test --tests "com.zimaai.codetrace.actions.AddToCodeTraceHandlerTest"
```

预期：FAIL，当前 handler 仍然只会 append source，不会读取聚焦节点作为插入锚点。

- [ ] **步骤 3：编写最少实现代码**

把 `src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceHandler.java` 的 source 插入段改成：

```java
        int sourceIndex = controller.addOrReuseNodeAfterFocusedNode(source);
        String sourceId = controller.state().currentDocument().nodes().get(sourceIndex).id();
        controller.preferSelectedNode(sourceId);
```

如果后续确认 target 也要跟着 source 插入到同一段落，再补一条同类的 controller 调用；当前这版先只改 source 的落点，避免把现有 target 流程一起改乱。

- [ ] **步骤 4：运行测试验证通过**

运行：

```powershell
.\gradlew.bat test --tests "com.zimaai.codetrace.actions.AddToCodeTraceHandlerTest" --tests "com.zimaai.codetrace.toolwindow.CodeTracePanelTest" --tests "com.zimaai.codetrace.toolwindow.CodeTraceControllerTest"
```

预期：PASS。

- [ ] **步骤 5：Commit**

```powershell
git add src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceHandler.java src/test/java/com/zimaai/codetrace/actions/AddToCodeTraceHandlerTest.java src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java src/main/java/com/zimaai/codetrace/toolwindow/NodeListReorderTransferHandler.java src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTest.java src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java
git commit -m "feat(code-trace): insert source after focused node"
```

## Task 4: 更新人工冒烟清单

**文件：**
- 修改：`docs/superpowers/plans/2026-05-29-code-trace-manual-smoke-checklist.md`

- [ ] **步骤 1：补充节点插入与拖拽检查项**

把 `Nodes And Links` 和 `Editor Popup Action` 段落改成：

```markdown
## Nodes And Links

1. Every node row text is exactly one line of code (`displayName`).
2. Select one node and click `Set as Source`; the same node remains selected and link status shows source id.
3. Select another node and click `Link To Here`; source/target styling appears and the target node remains selected.
4. Click `Unlink`; linked styling is removed and the current node remains selected.
5. Drag a node to a new position and confirm the order changes immediately after drop.
6. Drag a linked node and confirm the linked pair moves together.
7. Delete the currently selected node and confirm the node list ends with no selection.

## Editor Popup Action

1. Right-click in an in-project editor file and find `Add to code-trace`.
2. Ensure a node is selected in Tool Window before triggering the action.
3. Trigger `Add to code-trace` and confirm the source node is inserted directly below the selected node, then becomes selected after refresh.
4. If no node is selected, trigger `Add to code-trace` and confirm the new source is appended to the bottom.
5. If the same source line already exists, trigger the action again and confirm the existing source node is moved/kept at the insertion point and becomes selected.
6. If target confirmation appears and you choose `Yes`, target node and `DETECTED` link are created, but the selected node remains the source node.
```

- [ ] **步骤 2：保存并提交文档更新**

运行：

```powershell
git add docs/superpowers/plans/2026-05-29-code-trace-manual-smoke-checklist.md
git commit -m "docs(code-trace): update node insert smoke checks"
```

## Spec Coverage Check

- `右键菜单 Add to code-trace` 的插入位置对应 Task 1 和 Task 3：控制器支持按聚焦节点后插入，handler 读取并使用该聚焦节点。
- `当前没有聚焦节点时追加到最下方` 对应 Task 1 的 append 分支和 Task 3 的无聚焦测试。
- `把聚焦节点改为新增节点` 对应 Task 2 的面板同步与 Task 3 的 `preferredSelectedNodeId` 逻辑。
- `拖动节点改变位置` 对应 Task 1 的 `moveNodeOrPairToIndex(...)`、Task 2 的 transfer handler 和面板拖拽配置。
- `链接节点成组移动` 对应 Task 1 的 linked-pair reorder 测试。

## Placeholder Scan

- 没有 `TODO`、`TBD`、`后续实现`、`类似任务 N`。
- 每个任务都包含具体文件、测试代码、运行命令和 commit 命令。
- 新增 API 名称保持一致：`focusedNodeId()`、`setFocusedNodeId(...)`、`clearFocusedNodeId()`、`addOrReuseNodeAfterFocusedNode(...)`、`moveNodeOrPairToIndex(...)`。

