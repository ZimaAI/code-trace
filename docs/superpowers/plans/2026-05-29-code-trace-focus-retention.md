# Code Trace 节点选中保持实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 修复 `code-trace` 插件在节点操作后丢失当前选中项的问题，并让 `Add to code-trace` 在新增或复用 source 节点后自动切换选中到该节点。

**架构：** `CodeTracePanel` 继续持有稳定的节点选中状态，但在列表刷新期间增加同步保护，避免 `JList.setListData(...)` 触发的临时空选中事件污染真实状态。`CodeTraceState` 新增一次性的 `preferredSelectedNodeId`，由外部新增动作写入，`CodeTracePanel.rebuildView()` 在刷新时优先消费它；删除操作则在 UI 侧显式清空本地选中，保证刷新后没有默认兜底选中。

**技术栈：** Java 21、Swing、IntelliJ Platform SDK、JUnit 5

---

## Planned File Structure

### Selection state and UI refresh

- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
  - 增加节点列表同步保护标记，修正刷新后的恢复选中逻辑，删除“默认选第一个节点”的兜底。
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java`
  - 新增一次性的 `preferredSelectedNodeId` 状态，以及设置、读取、清除接口。
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java`
  - 暴露设置与消费优先选中节点的薄封装，避免 UI/action 直接修改 state 细节。

### External add action

- 修改：`src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceHandler.java`
  - 在新增或复用 source 节点后记录下一次刷新应选中的 source 节点 ID。

### Tests and smoke checks

- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java`
  - 增加一次性选中意图在 state 层的行为测试。
- 修改：`src/test/java/com/zimaai/codetrace/actions/AddToCodeTraceHandlerTest.java`
  - 增加 source 新增、source 复用、source+target 联动后的优先选中断言。
- 创建：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelSelectionTest.java`
  - 锁定刷新、删除和恢复逻辑对应的节点列表选中行为。
- 修改：`docs/superpowers/plans/2026-05-29-code-trace-manual-smoke-checklist.md`
  - 增加“普通操作保留选中”“新增后切到 source”“删除后清空选中”的人工回归步骤。

## Task 1: 为状态层补齐一次性选中意图

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java`
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java`

- [ ] **步骤 1：编写失败的状态与 controller 测试**

在 `src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java` 里追加：

```java
    @Test
    void storesAndClearsPreferredSelectedNodeIdAcrossRefreshPaths() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-4.json", documentWithThreeNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);

        controller.load("trace-4.json");
        controller.preferSelectedNode("node-2");

        assertEquals("node-2", controller.state().preferredSelectedNodeId());

        assertEquals("node-2", controller.consumePreferredSelectedNodeId());
        assertEquals(null, controller.state().preferredSelectedNodeId());

        controller.preferSelectedNode("node-3");
        controller.refreshCurrentFile();
        assertEquals(null, controller.state().preferredSelectedNodeId());
    }
```

如果测试文件还没有 `assertNull`，补充导入：

```java
import static org.junit.jupiter.api.Assertions.assertNull;
```

并把上面两个 `assertEquals(null, ...)` 改成：

```java
        assertNull(controller.state().preferredSelectedNodeId());
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```powershell
.\gradlew.bat test --tests "com.zimaai.codetrace.toolwindow.CodeTraceControllerTest"
```

预期：FAIL，编译错误指出 `preferredSelectedNodeId()`、`preferSelectedNode(...)`、`consumePreferredSelectedNodeId()` 不存在。

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

    void load(String fileName, TraceDocument document) {
        this.currentFileName = fileName;
        this.currentDocument = document;
        this.pendingLinkSourceId = null;
        this.preferredSelectedNodeId = null;
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
}
```

把 `src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java` 中 `state()` 之前追加：

```java
    public void preferSelectedNode(String nodeId) {
        state.setPreferredSelectedNodeId(nodeId);
    }

    public String consumePreferredSelectedNodeId() {
        return state.consumePreferredSelectedNodeId();
    }
```

- [ ] **步骤 4：运行测试验证通过**

运行：

```powershell
.\gradlew.bat test --tests "com.zimaai.codetrace.toolwindow.CodeTraceControllerTest"
```

预期：PASS。

- [ ] **步骤 5：Commit**

```powershell
git add src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java
git commit -m "feat(code-trace): add preferred node selection state"
```

## Task 2: 让外部新增动作记录 source 节点的优先选中意图

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceHandler.java`
- 修改：`src/test/java/com/zimaai/codetrace/actions/AddToCodeTraceHandlerTest.java`

- [ ] **步骤 1：编写失败的 handler 选中意图测试**

在 `src/test/java/com/zimaai/codetrace/actions/AddToCodeTraceHandlerTest.java` 里追加：

```java
    @Test
    void prefersSourceNodeAfterAddingSourceAndDetectedTarget() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.createNewFile("trace-2.json", "Trace 2");

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

        AddToCodeTraceHandler handler = new AddToCodeTraceHandler(
                controller,
                new FakeCaptureService(source, Optional.of(target)),
                new RecordingPrompts(true),
                () -> {
                });

        handler.handle(null, null, null);

        String sourceId = controller.state().currentDocument().nodes().get(0).id();
        assertEquals(sourceId, controller.state().preferredSelectedNodeId());
    }

    @Test
    void prefersReusedSourceNodeWhenSameSourceAlreadyExists() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.createNewFile("trace-3.json", "Trace 3");

        TraceNode existing = new TraceNode(
                "node-existing",
                "return authService.login(user);",
                "AuthController#login",
                "login(User user)",
                "src/AuthController.java",
                21,
                "JAVA",
                "",
                "AuthController#login(User)");
        controller.addNode(existing);

        AddToCodeTraceHandler handler = new AddToCodeTraceHandler(
                controller,
                new FakeCaptureService(existing, Optional.empty()),
                new RecordingPrompts(true),
                () -> {
                });

        handler.handle(null, null, null);

        assertEquals("node-existing", controller.state().preferredSelectedNodeId());
    }
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```powershell
.\gradlew.bat test --tests "com.zimaai.codetrace.actions.AddToCodeTraceHandlerTest"
```

预期：FAIL，断言失败，因为当前 handler 不会写入 `preferredSelectedNodeId`。

- [ ] **步骤 3：编写最少实现代码**

把 `src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceHandler.java` 的 `handle(...)` 方法改成：

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
        String sourceId = controller.state().currentDocument().nodes().get(sourceIndex).id();
        controller.preferSelectedNode(sourceId);

        Optional<TraceNode> detectedTarget = captureService.detectTarget(project, editor, psiFile);
        if (detectedTarget.isPresent()
                && prompts.confirmDetectedLink(project, source.displayName(), detectedTarget.get().displayName())) {
            int targetIndex = controller.addOrReuseNode(detectedTarget.get());
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

- [ ] **步骤 4：运行测试验证通过**

运行：

```powershell
.\gradlew.bat test --tests "com.zimaai.codetrace.actions.AddToCodeTraceHandlerTest" --tests "com.zimaai.codetrace.toolwindow.CodeTraceControllerTest"
```

预期：PASS。

- [ ] **步骤 5：Commit**

```powershell
git add src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceHandler.java src/test/java/com/zimaai/codetrace/actions/AddToCodeTraceHandlerTest.java src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java
git commit -m "feat(code-trace): prefer source node after external add"
```

## Task 3: 修正面板刷新时的选中恢复与删除清空逻辑

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
- 创建：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelSelectionTest.java`

- [ ] **步骤 1：编写失败的面板选中行为测试**

创建 `src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelSelectionTest.java`：

```java
package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.storage.TraceJsonMapper;
import com.zimaai.codetrace.storage.TraceStorageService;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeTracePanelSelectionTest {
    @TempDir
    Path tempDir;

    @Test
    void keepsSelectedNodeAfterRefresh() throws Exception {
        CodeTracePanel panel = createPanelWithThreeNodes();
        selectNode(panel, 1);

        panel.refreshFromExternalAction();

        assertEquals("node-2", selectedNodeId(panel));
    }

    @Test
    void prefersControllerRequestedNodeOnRefresh() throws Exception {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-1.json", documentWithThreeNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.load("trace-1.json");
        CodeTracePanel panel = new CodeTracePanel(controller);
        panel.reloadFromDisk();

        selectNode(panel, 0);
        controller.preferSelectedNode("node-3");

        panel.refreshFromExternalAction();

        assertEquals("node-3", selectedNodeId(panel));
    }

    @Test
    void clearsSelectionAfterDeletingSelectedNode() throws Exception {
        CodeTracePanel panel = createPanelWithThreeNodes();
        selectNode(panel, 1);

        clickButton(panel, "Delete Node");

        assertNull(selectedNodeId(panel));
    }

    private CodeTracePanel createPanelWithThreeNodes() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-1.json", documentWithThreeNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.load("trace-1.json");
        CodeTracePanel panel = new CodeTracePanel(controller);
        panel.reloadFromDisk();
        return panel;
    }

    private static TraceDocument documentWithThreeNodes() {
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
                List.of());
    }

    private static void selectNode(CodeTracePanel panel, int index) throws Exception {
        TraceEditorPanel editorPanel = editorPanel(panel);
        editorPanel.nodeList().setSelectedIndex(index);
    }

    private static void clickButton(CodeTracePanel panel, String text) {
        panel.findButton(text).doClick();
    }

    private static String selectedNodeId(CodeTracePanel panel) throws Exception {
        Field field = CodeTracePanel.class.getDeclaredField("selectedNodeId");
        field.setAccessible(true);
        return (String) field.get(panel);
    }

    private static TraceEditorPanel editorPanel(CodeTracePanel panel) throws Exception {
        Field field = CodeTracePanel.class.getDeclaredField("editorPanel");
        field.setAccessible(true);
        return (TraceEditorPanel) field.get(panel);
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```powershell
.\gradlew.bat test --tests "com.zimaai.codetrace.toolwindow.CodeTracePanelSelectionTest"
```

预期：FAIL，至少会出现以下一种失败：

- `keepsSelectedNodeAfterRefresh` 失败，因为刷新后会回退或被清空
- `prefersControllerRequestedNodeOnRefresh` 失败，因为 `CodeTracePanel` 没有消费优先选中意图
- `clearsSelectionAfterDeletingSelectedNode` 失败，因为当前实现会回退到第一个节点

- [ ] **步骤 3：编写最少实现代码**

把 `src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java` 改成：

```java
package com.zimaai.codetrace.toolwindow;

import com.intellij.ui.JBSplitter;
import com.zimaai.codetrace.model.TraceLinkKind;
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
    private boolean syncingNodeSelection;
    private String persistedTraceNote = "";
    private String persistedNodeNote = "";
    private String selectedNodeId;

    public CodeTracePanel(CodeTraceController controller) {
        this.controller = controller;
        editorPanel.nodeList().setCellRenderer(
                new LinkedNodeListCellRenderer(() -> controller.state().currentDocument(), () -> controller.state().pendingLinkSourceId()));
        configureLeftPaneActions();
        configureLayout();
        wireSelection();
        wireNoteButtons();
        wireNodeActions();
    }

    public JComponent getComponent() {
        return root;
    }

    public JButton findButton(String text) {
        JButton toolbarButton = buttons.get(text);
        if (toolbarButton != null) {
            return toolbarButton;
        }
        return switch (text) {
            case "Delete Node" -> editorPanel.deleteNodeButton();
            case "Edit Node" -> editorPanel.editNodeButton();
            case "Move Up" -> editorPanel.moveUpButton();
            case "Move Down" -> editorPanel.moveDownButton();
            case "Set as Source" -> editorPanel.setAsSourceButton();
            case "Link To Here" -> editorPanel.linkToHereButton();
            case "Unlink" -> editorPanel.unlinkButton();
            default -> null;
        };
    }

    public void reloadFromDisk() {
        controller.ensureAnyFileLoaded();
        rebuildView();
    }

    public void refreshFromExternalAction() {
        controller.refreshCurrentFile();
        rebuildView();
    }

    private void configureLeftPaneActions() {
        fileListPanel.configureActions(
                this::createFile,
                this::renameSelectedFile,
                this::copySelectedFile,
                this::deleteSelectedFile,
                this::refreshAndRepaint);
    }

    private void configureLayout() {
        JPanel toolbar = new JPanel();
        addButton(toolbar, "Refresh", this::refreshAndRepaint);
        addButton(toolbar, "Save Trace Note", this::saveTraceNote);
        addButton(toolbar, "Save Node Note", this::saveNodeNote);
        addButton(toolbar, "Set as Source", this::setSelectedAsSource);
        addButton(toolbar, "Link To Here", this::linkToSelectedNode);
        addButton(toolbar, "Unlink", this::unlinkSelectedNode);

        JBSplitter split = new JBSplitter(false, 0.25f);
        split.setHonorComponentsMinimumSize(false);
        split.setDividerWidth(12);
        split.setShowDividerControls(true);
        split.setAllowSwitchOrientationByMouseClick(false);
        split.setFirstComponent(fileListPanel.component());
        split.setSecondComponent(editorPanel.component());

        root.add(toolbar, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
    }

    private void wireSelection() {
        fileListPanel.list().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            String selected = fileListPanel.list().getSelectedValue();
            if (selected == null || selected.equals(controller.state().currentFileName())) {
                return;
            }
            controller.load(selected);
            rebuildView();
        });

        editorPanel.nodeList().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting() || syncingNodeSelection) {
                return;
            }
            TraceNode selected = editorPanel.nodeList().getSelectedValue();
            selectedNodeId = selected == null ? null : selected.id();
            syncSelectedNodeNote();
            refreshButtons();
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

    private void wireNoteButtons() {
        editorPanel.saveTraceNoteButton().addActionListener(event -> saveTraceNote());
        editorPanel.saveNodeNoteButton().addActionListener(event -> saveNodeNote());

        editorPanel.traceNote().getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshButtons();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshButtons();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshButtons();
            }
        });
        editorPanel.nodeNote().getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshButtons();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshButtons();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshButtons();
            }
        });
    }

    private void wireNodeActions() {
        editorPanel.editNodeButton().addActionListener(event -> editSelectedNode());
        editorPanel.deleteNodeButton().addActionListener(event -> deleteSelectedNode());
        editorPanel.moveUpButton().addActionListener(event -> moveSelectedNode(-1));
        editorPanel.moveDownButton().addActionListener(event -> moveSelectedNode(1));
        editorPanel.setAsSourceButton().addActionListener(event -> setSelectedAsSource());
        editorPanel.linkToHereButton().addActionListener(event -> linkToSelectedNode());
        editorPanel.unlinkButton().addActionListener(event -> unlinkSelectedNode());
    }

    private void addButton(JPanel toolbar, String label, Runnable action) {
        JButton button = new JButton(label);
        button.addActionListener(event -> action.run());
        buttons.put(label, button);
        toolbar.add(button);
    }

    private void createFile() {
        String fileName = JOptionPane.showInputDialog(root, "New JSON file name", "new-trace.json");
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        String normalized = fileName.endsWith(".json") ? fileName : fileName + ".json";
        controller.createNewFile(normalized, normalized.replace(".json", ""));
        rebuildView();
        fileListPanel.list().setSelectedValue(normalized, true);
    }

    private void renameSelectedFile() {
        String current = controller.state().currentFileName();
        if (current == null) {
            return;
        }
        String newName = JOptionPane.showInputDialog(root, "Rename file", current);
        if (newName == null || newName.isBlank()) {
            return;
        }
        String normalized = newName.endsWith(".json") ? newName : newName + ".json";
        controller.renameCurrentFile(normalized);
        rebuildView();
        fileListPanel.list().setSelectedValue(normalized, true);
    }

    private void copySelectedFile() {
        String current = controller.state().currentFileName();
        if (current == null) {
            return;
        }
        String newName = JOptionPane.showInputDialog(root, "Copy as", current.replace(".json", "-copy.json"));
        if (newName == null || newName.isBlank()) {
            return;
        }
        String normalized = newName.endsWith(".json") ? newName : newName + ".json";
        controller.copyCurrentFile(normalized);
        rebuildView();
    }

    private void deleteSelectedFile() {
        String current = controller.state().currentFileName();
        if (current == null) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(
                root,
                "Delete " + current + "?",
                "Confirm Delete",
                JOptionPane.OK_CANCEL_OPTION);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }
        controller.deleteCurrentFile();
        controller.ensureAnyFileLoaded();
        rebuildView();
    }

    private void refreshAndRepaint() {
        controller.refreshCurrentFile();
        rebuildView();
    }

    private void saveTraceNote() {
        if (controller.state().currentDocument() == null) {
            return;
        }
        controller.saveDescription(editorPanel.traceNote().getText());
        rebuildView();
    }

    private void saveNodeNote() {
        if (selectedNodeId == null) {
            return;
        }
        controller.saveNodeNote(selectedNodeId, editorPanel.nodeNote().getText());
        rebuildView();
    }

    private void setSelectedAsSource() {
        if (selectedNodeId == null) {
            return;
        }
        controller.setPendingLinkSource(selectedNodeId);
        rebuildView();
    }

    private void linkToSelectedNode() {
        if (selectedNodeId == null || controller.state().pendingLinkSourceId() == null) {
            return;
        }
        try {
            controller.linkPendingSourceTo(selectedNodeId, TraceLinkKind.MANUAL);
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(root, exception.getMessage());
        }
        rebuildView();
    }

    private void unlinkSelectedNode() {
        if (selectedNodeId == null) {
            return;
        }
        controller.unlinkNode(selectedNodeId);
        rebuildView();
    }

    private void editSelectedNode() {
        TraceNode existing = editorPanel.nodeList().getSelectedValue();
        if (existing == null) {
            return;
        }
        NodeInput input = showNodeDialog("Edit Node", existing);
        if (input == null) {
            return;
        }
        TraceNode updated = new TraceNode(
                existing.id(),
                input.displayName(),
                input.qualifiedName(),
                input.signature(),
                input.filePath(),
                input.line(),
                input.language(),
                input.note(),
                input.navigationHint());
        controller.updateNode(updated);
        rebuildView();
    }

    private void deleteSelectedNode() {
        if (selectedNodeId == null) {
            return;
        }
        selectedNodeId = null;
        controller.deleteNodeOrPair(editorPanel.nodeList().getSelectedValue().id());
        rebuildView();
    }

    private void moveSelectedNode(int offset) {
        if (selectedNodeId == null) {
            return;
        }
        controller.moveNodeOrPair(selectedNodeId, offset);
        rebuildView();
    }

    private NodeInput showNodeDialog(String title, TraceNode initial) {
        javax.swing.JTextField nameField = new javax.swing.JTextField(initial == null ? "" : initial.displayName());
        javax.swing.JTextField qualifiedField = new javax.swing.JTextField(initial == null ? "" : initial.qualifiedName());
        javax.swing.JTextField signatureField = new javax.swing.JTextField(initial == null ? "" : initial.signature());
        javax.swing.JTextField fileField = new javax.swing.JTextField(initial == null ? "" : initial.filePath());
        javax.swing.JTextField lineField = new javax.swing.JTextField(initial == null ? "1" : Integer.toString(initial.line()));
        javax.swing.JTextField languageField = new javax.swing.JTextField(initial == null ? "UNKNOWN" : initial.language());
        javax.swing.JTextField hintField = new javax.swing.JTextField(initial == null ? "" : initial.navigationHint());
        javax.swing.JTextField noteField = new javax.swing.JTextField(initial == null ? "" : initial.note());

        JPanel panel = new JPanel(new java.awt.GridLayout(0, 1));
        panel.add(new javax.swing.JLabel("Display Name"));
        panel.add(nameField);
        panel.add(new javax.swing.JLabel("Qualified Name"));
        panel.add(qualifiedField);
        panel.add(new javax.swing.JLabel("Signature"));
        panel.add(signatureField);
        panel.add(new javax.swing.JLabel("File Path"));
        panel.add(fileField);
        panel.add(new javax.swing.JLabel("Line"));
        panel.add(lineField);
        panel.add(new javax.swing.JLabel("Language"));
        panel.add(languageField);
        panel.add(new javax.swing.JLabel("Navigation Hint"));
        panel.add(hintField);
        panel.add(new javax.swing.JLabel("Node Note"));
        panel.add(noteField);

        int result = JOptionPane.showConfirmDialog(root, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }
        int line;
        try {
            line = Integer.parseInt(lineField.getText().trim());
        } catch (NumberFormatException exception) {
            line = 1;
        }
        return new NodeInput(
                nameField.getText().trim(),
                qualifiedField.getText().trim(),
                signatureField.getText().trim(),
                fileField.getText().trim(),
                Math.max(1, line),
                languageField.getText().trim().isEmpty() ? "UNKNOWN" : languageField.getText().trim(),
                noteField.getText(),
                hintField.getText().trim());
    }

    private void rebuildView() {
        if (controller.state().currentFileName() == null) {
            controller.ensureAnyFileLoaded();
        }
        List<String> files = controller.loadFileNames();
        fileListPanel.list().setListData(files.toArray(String[]::new));

        var document = controller.state().currentDocument();
        if (document == null) {
            syncingTraceNote = true;
            editorPanel.traceNote().setText("");
            syncingTraceNote = false;
            persistedTraceNote = "";
            syncingNodeSelection = true;
            editorPanel.nodeList().setListData(new TraceNode[0]);
            syncingNodeSelection = false;
            selectedNodeId = null;
            syncingNodeNote = true;
            editorPanel.nodeNote().setText("");
            syncingNodeNote = false;
            persistedNodeNote = "";
            editorPanel.linkStatus().setText("Link source: none");
            refreshButtons();
            return;
        }

        if (controller.state().currentFileName() != null) {
            fileListPanel.list().setSelectedValue(controller.state().currentFileName(), true);
        }

        syncingTraceNote = true;
        persistedTraceNote = document.description() == null ? "" : document.description();
        editorPanel.traceNote().setText(persistedTraceNote);
        syncingTraceNote = false;

        syncingNodeSelection = true;
        editorPanel.nodeList().setListData(document.nodes().toArray(TraceNode[]::new));
        restoreSelection(document.nodes());
        syncingNodeSelection = false;
        editorPanel.linkStatus().setText("Link source: "
                + (controller.state().pendingLinkSourceId() == null ? "none" : controller.state().pendingLinkSourceId()));

        syncSelectedNodeNote();
        refreshButtons();
    }

    private void syncSelectedNodeNote() {
        TraceNode selected = findSelectedNode();
        syncingNodeNote = true;
        persistedNodeNote = selected == null || selected.note() == null ? "" : selected.note();
        editorPanel.nodeNote().setText(persistedNodeNote);
        syncingNodeNote = false;
    }

    private void refreshButtons() {
        var document = controller.state().currentDocument();
        boolean hasDocument = document != null;
        boolean hasSelection = findSelectedNode() != null;
        boolean hasPendingSource = controller.state().pendingLinkSourceId() != null;

        if (hasDocument && !syncingTraceNote) {
            editorPanel.saveTraceNoteButton().setEnabled(!persistedTraceNote.equals(editorPanel.traceNote().getText()));
        } else {
            editorPanel.saveTraceNoteButton().setEnabled(false);
        }

        if (hasSelection && !syncingNodeNote) {
            editorPanel.saveNodeNoteButton().setEnabled(!persistedNodeNote.equals(editorPanel.nodeNote().getText()));
        } else {
            editorPanel.saveNodeNoteButton().setEnabled(false);
        }

        editorPanel.editNodeButton().setEnabled(hasSelection);
        editorPanel.deleteNodeButton().setEnabled(hasSelection);
        editorPanel.moveUpButton().setEnabled(hasSelection);
        editorPanel.moveDownButton().setEnabled(hasSelection);
        editorPanel.setAsSourceButton().setEnabled(hasSelection);
        editorPanel.linkToHereButton().setEnabled(hasSelection && hasPendingSource);
        editorPanel.unlinkButton().setEnabled(hasSelection);
    }

    private TraceNode findSelectedNode() {
        if (selectedNodeId == null || controller.state().currentDocument() == null) {
            return null;
        }
        return controller.state().currentDocument().nodes().stream()
                .filter(node -> node.id().equals(selectedNodeId))
                .findFirst()
                .orElse(null);
    }

    private void restoreSelection(List<TraceNode> nodes) {
        if (nodes.isEmpty()) {
            selectedNodeId = null;
            editorPanel.nodeList().clearSelection();
            controller.consumePreferredSelectedNodeId();
            return;
        }

        String preferredSelectedNodeId = controller.consumePreferredSelectedNodeId();
        if (preferredSelectedNodeId != null) {
            for (int i = 0; i < nodes.size(); i++) {
                if (nodes.get(i).id().equals(preferredSelectedNodeId)) {
                    selectedNodeId = preferredSelectedNodeId;
                    editorPanel.nodeList().setSelectedIndex(i);
                    return;
                }
            }
            selectedNodeId = null;
            editorPanel.nodeList().clearSelection();
            return;
        }

        if (selectedNodeId != null) {
            for (int i = 0; i < nodes.size(); i++) {
                if (nodes.get(i).id().equals(selectedNodeId)) {
                    editorPanel.nodeList().setSelectedIndex(i);
                    return;
                }
            }
        }

        selectedNodeId = null;
        editorPanel.nodeList().clearSelection();
    }

    private record NodeInput(
            String displayName,
            String qualifiedName,
            String signature,
            String filePath,
            int line,
            String language,
            String note,
            String navigationHint) {
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：

```powershell
.\gradlew.bat test --tests "com.zimaai.codetrace.toolwindow.CodeTracePanelSelectionTest" --tests "com.zimaai.codetrace.actions.AddToCodeTraceHandlerTest" --tests "com.zimaai.codetrace.toolwindow.CodeTraceControllerTest"
```

预期：PASS。

- [ ] **步骤 5：Commit**

```powershell
git add src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelSelectionTest.java src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceHandler.java src/test/java/com/zimaai/codetrace/actions/AddToCodeTraceHandlerTest.java src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java
git commit -m "fix(code-trace): preserve node selection across refreshes"
```

## Task 4: 更新人工冒烟清单并执行完整回归

**文件：**
- 修改：`docs/superpowers/plans/2026-05-29-code-trace-manual-smoke-checklist.md`

- [ ] **步骤 1：补充节点选中相关人工检查项**

把 `docs/superpowers/plans/2026-05-29-code-trace-manual-smoke-checklist.md` 的 `Nodes And Links` 与 `Editor Popup Action` 部分改成：

```markdown
## Nodes And Links

1. Every node row text is exactly one line of code (`displayName`).
2. Select one node and click `Set as Source`; the same node remains selected and link status shows source id.
3. Select another node and click `Link To Here`; source/target styling appears and the target node remains selected.
4. Click `Unlink`; linked styling is removed and the current node remains selected.
5. Use `Move Up` or `Move Down` on a selected node and confirm the same node remains selected after the list refreshes.
6. Delete the currently selected node and confirm the node list ends with no selection.

## Editor Popup Action

1. Right-click in an in-project editor file and find `Add to code-trace`.
2. Ensure a JSON is selected in Tool Window before triggering the action.
3. Trigger `Add to code-trace` and confirm the source node becomes the selected node in the Tool Window.
4. If the same source line already exists, trigger the action again and confirm the existing source node becomes selected instead of keeping the previous selection.
5. If target confirmation appears and you choose `Yes`, target node and `DETECTED` link are created, but the selected node remains the source node.
6. Trigger the action from a file outside the current project root and confirm a rejection message appears and the trace content does not change.
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
git commit -m "docs(code-trace): add node selection retention smoke checks"
```

## Spec Coverage Check

- `普通节点操作后保持原节点选中` 对应 Task 3：刷新、编辑、移动、link、unlink 的面板行为测试与实现。
- `外部新增后选中 source 节点` 对应 Task 2 和 Task 3：handler 写入优先选中意图，panel 在刷新时消费它。
- `source 复用已有节点时也切过去选中` 对应 Task 2：复用场景测试覆盖。
- `同一次新增里即使还新增 target，最终仍选 source` 对应 Task 2：source + target + detected link 测试覆盖。
- `删除当前节点后清空选中` 对应 Task 3：删除测试与恢复逻辑覆盖。
- `不再默认选中第一个节点` 对应 Task 3：`restoreSelection(...)` 的清空逻辑与测试覆盖。

## Placeholder Scan

- 没有 `TODO`、`TBD`、`后续实现`、`类似任务 N`。
- 每个任务都给出了具体文件、测试代码、运行命令和 commit 命令。
- 所有新增 API 名称在任务间保持一致：`preferredSelectedNodeId()`、`preferSelectedNode(...)`、`consumePreferredSelectedNodeId()`。

