# code-trace UI 布局优化实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将 code-trace Tool Window 优化为左侧文件列表、中间节点主工作区、右侧备注侧栏的三栏体验，并把常用节点操作移入表格行内操作列。

**架构：** 保持现有 `toolwindow` 包和 trace 数据模型不变。`TraceEditorPanel` 负责布局和 Swing 组件，`CodeTracePanel` 负责事件绑定、链接源状态和状态栏反馈，新增行内操作列组件负责每行的 Edit/Delete/Move Up/Move Down 图标操作。

**技术栈：** Java 21、IntelliJ Platform Swing UI、JUnit 5、Gradle Wrapper。

---

## 已批准规格

- 规格文件：`docs/superpowers/specs/2026-06-08-code-trace-ui-layout-optimization-design.md`
- 当前约束：不实现节点树、右键菜单、排序、批量删除/移动、trace JSON schema 变更、独立 toast 系统。

## 文件结构

### 修改文件

- `docs/superpowers/specs/2026-06-08-code-trace-ui-layout-optimization-design.md`
  - 职责：将状态从 `待书面审查` 更新为 `已批准`。

- `src/main/java/com/zimaai/codetrace/toolwindow/NodeTableModel.java`
  - 职责：将表格模型扩展为 4 列，新增 `操作` 列并返回当前行 `TraceNode`。

- `src/main/java/com/zimaai/codetrace/toolwindow/FilteredNodeTableModel.java`
  - 职责：透传新增 `操作` 列，保持过滤后的可见节点表格一致。

- `src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java`
  - 职责：重排为中间节点主区 + 右侧备注侧栏；节点工具栏仅保留链接组和视图组；提供行内操作列安装所需访问器。

- `src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
  - 职责：安装行内操作列 renderer/editor；将 Edit/Delete/Move 逻辑改为可按具体行节点执行；增加删除确认、链接源状态文案、操作反馈、列宽适配。

### 新增文件

- `src/main/java/com/zimaai/codetrace/toolwindow/NodeRowAction.java`
  - 职责：定义行内操作枚举：`EDIT`、`DELETE`、`MOVE_UP`、`MOVE_DOWN`。

- `src/main/java/com/zimaai/codetrace/toolwindow/NodeRowActionsPanel.java`
  - 职责：创建一组图标按钮，可作为 renderer/editor 的共享 UI；测试可通过包内方法触发按钮。

- `src/main/java/com/zimaai/codetrace/toolwindow/NodeRowActionsRenderer.java`
  - 职责：在表格 `操作` 列渲染 4 个图标按钮。

- `src/main/java/com/zimaai/codetrace/toolwindow/NodeRowActionsEditor.java`
  - 职责：在表格 `操作` 列处理按钮点击，并回调 `CodeTracePanel`。

### 测试文件

- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/NodeTableModelTest.java`
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/FilteredNodeTableModelTest.java`
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/TraceEditorPanelTest.java`
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTest.java`
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelNavigationTest.java`
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelColumnWidthTest.java`
- 新增：`src/test/java/com/zimaai/codetrace/toolwindow/NodeRowActionsPanelTest.java`
- 新增：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelRowActionTest.java`
- 新增：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelLinkSourceStatusTest.java`

---

## 任务 1：扩展表格模型，新增 `操作` 列

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/NodeTableModel.java`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/FilteredNodeTableModel.java`
- 测试：`src/test/java/com/zimaai/codetrace/toolwindow/NodeTableModelTest.java`
- 测试：`src/test/java/com/zimaai/codetrace/toolwindow/FilteredNodeTableModelTest.java`

- [ ] **步骤 1：编写失败的 NodeTableModel 测试**

在 `NodeTableModelTest` 增加：

```java
@Test
void exposesActionColumnWithNodeValue() {
    List<TraceNode> nodes = List.of(createNode("1", "Root", null));
    NodeTableModel model = new NodeTableModel(nodes, Map.of("1", "1"), List.of());

    assertEquals(4, model.getColumnCount());
    assertEquals("操作", model.getColumnName(3));
    assertEquals(TraceNode.class, model.getColumnClass(3));
    assertEquals(nodes.get(0), model.getValueAt(0, 3));
}
```

- [ ] **步骤 2：编写失败的 FilteredNodeTableModel 测试**

在 `FilteredNodeTableModelTest` 增加：

```java
@Test
void actionColumnReturnsVisibleNode() {
    document = new TraceDocument(3, "test", "test", "test", Instant.now(), Instant.now(), nodes, List.of(), Set.of("1"));
    FilteredNodeTableModel filteredModel = new FilteredNodeTableModel(sourceModel, document);

    assertEquals(4, filteredModel.getColumnCount());
    assertEquals("操作", filteredModel.getColumnName(3));
    assertEquals(TraceNode.class, filteredModel.getColumnClass(3));
    assertEquals(nodes.get(0), filteredModel.getValueAt(0, 3));
}
```

- [ ] **步骤 3：运行测试验证失败**

运行：

```powershell
.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.NodeTableModelTest --tests com.zimaai.codetrace.toolwindow.FilteredNodeTableModelTest
```

预期：FAIL。失败点应包含 `expected: <4> but was: <3>` 或 `COLUMN_NAMES[3]` 访问失败。

- [ ] **步骤 4：实现 NodeTableModel 的新增列**

在 `NodeTableModel` 中将列定义改为：

```java
private static final String[] COLUMN_NAMES = {"编号", "节点名称", "链接关系", "操作"};
```

更新 `getColumnCount()`：

```java
@Override
public int getColumnCount() {
    return COLUMN_NAMES.length;
}
```

更新 `getValueAt()`：

```java
return switch (columnIndex) {
    case 0 -> numberMap.getOrDefault(node.id(), "");
    case 1 -> node;
    case 2 -> node;
    case 3 -> node;
    default -> null;
};
```

更新 `getColumnClass()`：

```java
return switch (columnIndex) {
    case 0 -> String.class;
    case 1, 2, 3 -> TraceNode.class;
    default -> Object.class;
};
```

- [ ] **步骤 5：实现 FilteredNodeTableModel 的新增列透传**

在 `FilteredNodeTableModel.getValueAt()` 中增加：

```java
case 3 -> node;
```

保持 `getColumnCount()`、`getColumnClass()`、`getColumnName()` 继续委托 `sourceModel`。

- [ ] **步骤 6：运行测试验证通过**

运行：

```powershell
.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.NodeTableModelTest --tests com.zimaai.codetrace.toolwindow.FilteredNodeTableModelTest
```

预期：PASS。

- [ ] **步骤 7：Commit**

```powershell
git add src/main/java/com/zimaai/codetrace/toolwindow/NodeTableModel.java src/main/java/com/zimaai/codetrace/toolwindow/FilteredNodeTableModel.java src/test/java/com/zimaai/codetrace/toolwindow/NodeTableModelTest.java src/test/java/com/zimaai/codetrace/toolwindow/FilteredNodeTableModelTest.java
git commit -m "feat: add node table action column"
```

---

## 任务 2：重排 TraceEditorPanel 为节点主区和右侧备注侧栏

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java`
- 测试：`src/test/java/com/zimaai/codetrace/toolwindow/TraceEditorPanelTest.java`

- [ ] **步骤 1：编写失败的工具栏分组测试**

在 `TraceEditorPanelTest` 增加 helper：

```java
private static boolean toolbarContainsButtonText(JPanel toolbar, String text) {
    for (java.awt.Component component : toolbar.getComponents()) {
        if (component instanceof JButton button && text.equals(button.getText())) {
            return true;
        }
    }
    return false;
}
```

更新 `usesWrapLayoutForNodeToolbarAndDisablesJumpButtonByDefault()` 中按钮文本断言：

```java
assertEquals("Go to Linked Node", goToLinked.getText());
```

新增测试：

```java
@Test
void nodeToolbarContainsOnlyLinkAndViewActions() {
    TraceEditorPanel panel = new TraceEditorPanel();
    JPanel toolbar = panel.nodeToolbar();

    assertFalse(toolbarContainsButtonText(toolbar, "Edit Node"));
    assertFalse(toolbarContainsButtonText(toolbar, "Delete Node"));
    assertFalse(toolbarContainsButtonText(toolbar, "Move Up"));
    assertFalse(toolbarContainsButtonText(toolbar, "Move Down"));
    assertTrue(toolbarContainsButtonText(toolbar, "Set as Source"));
    assertTrue(toolbarContainsButtonText(toolbar, "Link To Here"));
    assertTrue(toolbarContainsButtonText(toolbar, "Unlink"));
    assertTrue(toolbarContainsButtonText(toolbar, "Go to Linked Node"));
    assertTrue(toolbarContainsButtonText(toolbar, "Expand All"));
    assertTrue(toolbarContainsButtonText(toolbar, "Collapse All"));
}
```

- [ ] **步骤 2：编写失败的右侧备注侧栏测试**

在 `TraceEditorPanelTest` 增加：

```java
@Test
void exposesSeparateNodeMainAndNotesPanels() {
    TraceEditorPanel panel = new TraceEditorPanel();

    assertNotNull(panel.nodeMainPanel());
    assertNotNull(panel.notesPanel());
    assertTrue(panel.nodeMainPanel().isAncestorOf(panel.nodeTable()));
    assertTrue(panel.notesPanel().isAncestorOf(panel.traceNote()));
    assertTrue(panel.notesPanel().isAncestorOf(panel.nodeNote()));
}
```

- [ ] **步骤 3：运行测试验证失败**

运行：

```powershell
.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.TraceEditorPanelTest
```

预期：FAIL。失败点应包含 `Go To Linked` 文本不匹配、工具栏仍包含 Edit/Delete/Move 按钮，或 `nodeMainPanel()` / `notesPanel()` 方法不存在。

- [ ] **步骤 4：实现布局字段和访问器**

在 `TraceEditorPanel` 增加字段：

```java
private final JPanel nodeMainPanel = new JPanel(new BorderLayout());
private final JPanel notesPanel = new JPanel(new BorderLayout());
```

增加包内访问器：

```java
JPanel nodeMainPanel() {
    return nodeMainPanel;
}

JPanel notesPanel() {
    return notesPanel;
}
```

将 `goToLinkedButton` 文本改为：

```java
private final JButton goToLinkedButton = new JButton("Go to Linked Node", AllIcons.Actions.Find);
```

- [ ] **步骤 5：重写 TraceEditorPanel 构造布局**

将构造函数中的布局重排为：

```java
JPanel traceNotePanel = new JPanel(new BorderLayout());
traceNotePanel.setBorder(JBUI.Borders.empty(0, 0, 8, 0));
traceNotePanel.add(new JBScrollPane(traceNote), BorderLayout.CENTER);
traceNotePanel.add(saveTraceNoteButton, BorderLayout.SOUTH);

JPanel nodeNotePanel = new JPanel(new BorderLayout());
nodeNotePanel.add(new JBScrollPane(nodeNote), BorderLayout.CENTER);
nodeNotePanel.add(saveNodeNoteButton, BorderLayout.SOUTH);

JSplitPane notesSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, traceNotePanel, nodeNotePanel);
notesSplit.setResizeWeight(0.45d);
notesPanel.add(notesSplit, BorderLayout.CENTER);

nodeMainPanel.setBorder(JBUI.Borders.empty(4, 0, 0, 8));
nodeMainPanel.add(nodeToolbar, BorderLayout.NORTH);
nodeMainPanel.add(new JBScrollPane(nodeTable), BorderLayout.CENTER);
nodeMainPanel.add(linkStatus, BorderLayout.SOUTH);

JSplitPane editorSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, nodeMainPanel, notesPanel);
editorSplit.setResizeWeight(0.78d);
root.add(editorSplit, BorderLayout.CENTER);
```

保留现有表格配置、文本区配置、`goToLinkedButton.setEnabled(false)` 和 `addTooltips()`。

- [ ] **步骤 6：重写 configureNodeToolbar**

移除 Edit/Delete/Move 按钮，只保留链接组和视图组：

```java
private void configureNodeToolbar() {
    nodeToolbar.add(setAsSourceButton);
    nodeToolbar.add(linkToHereButton);
    nodeToolbar.add(unlinkButton);
    nodeToolbar.add(goToLinkedButton);
    nodeToolbar.add(new JSeparator(SwingConstants.VERTICAL));
    nodeToolbar.add(expandAllButton);
    nodeToolbar.add(collapseAllButton);
}
```

保留 `editNodeButton`、`deleteNodeButton`、`moveUpButton`、`moveDownButton` 字段、getter 和 tooltip，作为任务 4 迁移 `CodeTracePanel` 事件绑定前的过渡 API。任务 2 只把这些按钮从 `nodeToolbar` 中移除，确保中间提交仍可编译；任务 4 接入行内操作后再删除旧 selected-action 绑定。

- [ ] **步骤 7：运行测试验证通过**

运行：

```powershell
.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.TraceEditorPanelTest
```

预期：PASS。

- [ ] **步骤 8：Commit**

```powershell
git add src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java src/test/java/com/zimaai/codetrace/toolwindow/TraceEditorPanelTest.java
git commit -m "feat: restructure trace editor layout"
```

---

## 任务 3：新增行内操作列组件

**文件：**
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/NodeRowAction.java`
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/NodeRowActionsPanel.java`
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/NodeRowActionsRenderer.java`
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/NodeRowActionsEditor.java`
- 测试：`src/test/java/com/zimaai/codetrace/toolwindow/NodeRowActionsPanelTest.java`

- [ ] **步骤 1：编写失败的 NodeRowActionsPanel 测试**

创建 `NodeRowActionsPanelTest`：

```java
package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.zimaai.codetrace.model.TraceNode;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;

class NodeRowActionsPanelTest {
    @Test
    void createsIconButtonsForEachRowAction() {
        NodeRowActionsPanel panel = new NodeRowActionsPanel(true, (action, node) -> {});

        for (NodeRowAction action : NodeRowAction.values()) {
            JButton button = panel.button(action);
            assertNotNull(button, action + " button should exist");
            assertNotNull(button.getIcon(), action + " button should have an icon");
            assertNotNull(button.getToolTipText(), action + " button should have a tooltip");
        }
    }

    @Test
    void clickPassesCurrentNodeAndAction() {
        AtomicReference<NodeRowAction> capturedAction = new AtomicReference<>();
        AtomicReference<TraceNode> capturedNode = new AtomicReference<>();
        TraceNode node = new TraceNode("node-1", "Node 1", "", "", "", 1, "JAVA", "", "");
        NodeRowActionsPanel panel = new NodeRowActionsPanel(true, (action, clickedNode) -> {
            capturedAction.set(action);
            capturedNode.set(clickedNode);
        });
        panel.setNode(node);

        panel.button(NodeRowAction.DELETE).doClick();

        assertEquals(NodeRowAction.DELETE, capturedAction.get());
        assertEquals(node, capturedNode.get());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```powershell
.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.NodeRowActionsPanelTest
```

预期：FAIL。失败点应为 `NodeRowAction` 或 `NodeRowActionsPanel` 不存在。

- [ ] **步骤 3：创建 NodeRowAction**

创建 `NodeRowAction.java`：

```java
package com.zimaai.codetrace.toolwindow;

enum NodeRowAction {
    EDIT,
    DELETE,
    MOVE_UP,
    MOVE_DOWN
}
```

- [ ] **步骤 4：创建 NodeRowActionsPanel**

创建 `NodeRowActionsPanel.java`，使用 IntelliJ 图标：

```java
package com.zimaai.codetrace.toolwindow;

import com.intellij.icons.AllIcons;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.FlowLayout;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.JButton;
import javax.swing.JPanel;

final class NodeRowActionsPanel extends JPanel {
    private final Map<NodeRowAction, JButton> buttons = new EnumMap<>(NodeRowAction.class);
    private TraceNode node;

    NodeRowActionsPanel(boolean interactive, BiConsumer<NodeRowAction, TraceNode> actionHandler) {
        super(new FlowLayout(FlowLayout.CENTER, 2, 0));
        setOpaque(true);
        addButton(NodeRowAction.EDIT, new JButton(AllIcons.Actions.Edit), "Edit node", interactive, actionHandler);
        addButton(NodeRowAction.DELETE, new JButton(AllIcons.General.Remove), "Delete node", interactive, actionHandler);
        addButton(NodeRowAction.MOVE_UP, new JButton(AllIcons.General.ArrowUp), "Move node up", interactive, actionHandler);
        addButton(NodeRowAction.MOVE_DOWN, new JButton(AllIcons.General.ArrowDown), "Move node down", interactive, actionHandler);
    }

    void setNode(TraceNode node) {
        this.node = node;
    }

    JButton button(NodeRowAction action) {
        return buttons.get(action);
    }

    private void addButton(
            NodeRowAction action,
            JButton button,
            String tooltip,
            boolean interactive,
            BiConsumer<NodeRowAction, TraceNode> actionHandler) {
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setMargin(com.intellij.util.ui.JBUI.insets(1));
        button.setEnabled(interactive);
        if (interactive) {
            button.addActionListener(event -> {
                if (node != null) {
                    actionHandler.accept(action, node);
                }
            });
        }
        buttons.put(action, button);
        add(button);
    }
}
```

- [ ] **步骤 5：创建 renderer 和 editor**

创建 `NodeRowActionsRenderer.java`：

```java
package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceNode;
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

final class NodeRowActionsRenderer implements TableCellRenderer {
    private final NodeRowActionsPanel panel = new NodeRowActionsPanel(false, (action, node) -> {});

    @Override
    public Component getTableCellRendererComponent(
            JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (value instanceof TraceNode node) {
            panel.setNode(node);
        }
        panel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        return panel;
    }
}
```

创建 `NodeRowActionsEditor.java`：

```java
package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceNode;
import java.awt.Component;
import java.util.function.BiConsumer;
import javax.swing.AbstractCellEditor;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;

final class NodeRowActionsEditor extends AbstractCellEditor implements TableCellEditor {
    private final NodeRowActionsPanel panel;

    NodeRowActionsEditor(BiConsumer<NodeRowAction, TraceNode> actionHandler) {
        panel = new NodeRowActionsPanel(true, (action, node) -> {
            stopCellEditing();
            actionHandler.accept(action, node);
        });
    }

    @Override
    public Object getCellEditorValue() {
        return null;
    }

    @Override
    public Component getTableCellEditorComponent(
            JTable table, Object value, boolean isSelected, int row, int column) {
        if (value instanceof TraceNode node) {
            panel.setNode(node);
        }
        panel.setBackground(table.getSelectionBackground());
        return panel;
    }
}
```

- [ ] **步骤 6：运行测试验证通过**

运行：

```powershell
.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.NodeRowActionsPanelTest
```

预期：PASS。

- [ ] **步骤 7：Commit**

```powershell
git add src/main/java/com/zimaai/codetrace/toolwindow/NodeRowAction.java src/main/java/com/zimaai/codetrace/toolwindow/NodeRowActionsPanel.java src/main/java/com/zimaai/codetrace/toolwindow/NodeRowActionsRenderer.java src/main/java/com/zimaai/codetrace/toolwindow/NodeRowActionsEditor.java src/test/java/com/zimaai/codetrace/toolwindow/NodeRowActionsPanelTest.java
git commit -m "feat: add node row action components"
```

---

## 任务 4：在 CodeTracePanel 接入行内操作和删除确认

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelNavigationTest.java`
- 测试：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelRowActionTest.java`

- [ ] **步骤 1：编写失败的行内操作测试**

创建 `CodeTracePanelRowActionTest`：

```java
package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.storage.TraceJsonMapper;
import com.zimaai.codetrace.storage.TraceStorageService;
import java.awt.Component;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeTracePanelRowActionTest {
    @TempDir
    Path tempDir;

    @Test
    void tableInstallsActionColumnRendererAndEditor() {
        CodeTracePanel panel = panelFor(documentWithTwoNodes());
        JTable table = panel.editorPanel().nodeTable();

        assertEquals("操作", table.getColumnModel().getColumn(3).getHeaderValue());
        assertEquals(NodeRowActionsRenderer.class, table.getColumnModel().getColumn(3).getCellRenderer().getClass());
        assertEquals(NodeRowActionsEditor.class, table.getColumnModel().getColumn(3).getCellEditor().getClass());
    }

    @Test
    void moveDownRowActionMovesThatRowNode() {
        CodeTracePanel panel = panelFor(documentWithTwoNodes());
        clickRowAction(panel, 0, NodeRowAction.MOVE_DOWN);

        TraceDocument document = controller(panel).state().currentDocument();
        assertEquals("node-2", document.nodes().get(0).id());
        assertEquals("node-1", document.nodes().get(1).id());
    }

    @Test
    void deleteRowActionRequiresConfirmation() {
        CodeTracePanel panel = panelFor(documentWithTwoNodes());
        panel.setConfirmNodeDeleteForTest(node -> false);

        clickRowAction(panel, 0, NodeRowAction.DELETE);
        assertEquals(2, controller(panel).state().currentDocument().nodes().size());

        panel.setConfirmNodeDeleteForTest(node -> true);
        clickRowAction(panel, 0, NodeRowAction.DELETE);
        assertEquals(1, controller(panel).state().currentDocument().nodes().size());
        assertFalse(panel.editorPanel().goToLinkedButton().isEnabled());
    }

    private CodeTracePanel panelFor(TraceDocument document) {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-1.json", document);
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        CodeTracePanel panel = new CodeTracePanel(controller);
        panel.reloadFromDisk();
        return panel;
    }

    private static void clickRowAction(CodeTracePanel panel, int row, NodeRowAction action) {
        JTable table = panel.editorPanel().nodeTable();
        Object value = table.getValueAt(row, 3);
        Component component = table.getColumnModel().getColumn(3).getCellEditor()
                .getTableCellEditorComponent(table, value, true, row, 3);
        ((NodeRowActionsPanel) component).button(action).doClick();
    }

    private static CodeTraceController controller(CodeTracePanel panel) {
        try {
            java.lang.reflect.Field field = CodeTracePanel.class.getDeclaredField("controller");
            field.setAccessible(true);
            return (CodeTraceController) field.get(panel);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static TraceDocument documentWithTwoNodes() {
        return new TraceDocument(
                3,
                "trace-1",
                "Trace 1",
                "",
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(
                        new TraceNode("node-1", "first", "A#a", "a()", "A.java", 10, "JAVA", "", "A#a"),
                        new TraceNode("node-2", "second", "B#b", "b()", "B.java", 20, "JAVA", "", "B#b")),
                List.of(),
                Set.of());
    }
}
```

- [ ] **步骤 2：更新导航测试避免使用旧 toolbar Delete 按钮**

在 `CodeTracePanelNavigationTest.disablesMissingEndpointAndClearsButtonAfterUnlinkOrDelete()` 中，将：

```java
deletePanel.editorPanel().deleteNodeButton().doClick();
```

替换为使用新行内操作 helper。可以在测试类底部加入：

```java
private static void clickRowAction(CodeTracePanel panel, int row, NodeRowAction action) {
    javax.swing.JTable table = panel.editorPanel().nodeTable();
    Object value = table.getValueAt(row, 3);
    java.awt.Component component = table.getColumnModel().getColumn(3).getCellEditor()
            .getTableCellEditorComponent(table, value, true, row, 3);
    ((NodeRowActionsPanel) component).button(action).doClick();
}
```

并在删除测试中先设置确认：

```java
deletePanel.setConfirmNodeDeleteForTest(node -> true);
clickRowAction(deletePanel, 0, NodeRowAction.DELETE);
```

- [ ] **步骤 3：运行测试验证失败**

运行：

```powershell
.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.CodeTracePanelRowActionTest --tests com.zimaai.codetrace.toolwindow.CodeTracePanelNavigationTest
```

预期：FAIL。失败点应为 action column editor/renderer 未安装、`setConfirmNodeDeleteForTest` 不存在，或旧 toolbar delete getter 已不存在。

- [ ] **步骤 4：修改 CodeTracePanel 的节点动作绑定**

在 `wireNodeActions()` 中移除旧的 selected Edit/Delete/Move 绑定，只保留：

```java
editorPanel.setAsSourceButton().addActionListener(event -> setSelectedAsSource());
editorPanel.linkToHereButton().addActionListener(event -> linkToSelectedNode());
editorPanel.unlinkButton().addActionListener(event -> unlinkSelectedNode());
editorPanel.goToLinkedButton().addActionListener(event -> goToLinked());
```

新增删除确认字段：

```java
private java.util.function.Predicate<TraceNode> confirmNodeDelete = node -> JOptionPane.showConfirmDialog(
        root,
        "Delete node " + node.displayName() + "?",
        "Confirm Delete",
        JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION;
```

新增包内测试 setter：

```java
void setConfirmNodeDeleteForTest(java.util.function.Predicate<TraceNode> confirmNodeDelete) {
    this.confirmNodeDelete = java.util.Objects.requireNonNull(confirmNodeDelete, "confirmNodeDelete");
}
```

- [ ] **步骤 5：安装 action column**

在 `rebuildView()` 中配置完第 2 列 renderer 后，增加第 3 列配置：

```java
editorPanel.nodeTable().getColumnModel().getColumn(3).setResizable(false);
editorPanel.nodeTable().getColumnModel().getColumn(3).setCellRenderer(new NodeRowActionsRenderer());
editorPanel.nodeTable().getColumnModel().getColumn(3).setCellEditor(
        new NodeRowActionsEditor(this::handleRowAction));
```

新增 handler：

```java
private void handleRowAction(NodeRowAction action, TraceNode node) {
    selectNode(node);
    switch (action) {
        case EDIT -> editNode(node);
        case DELETE -> deleteNode(node);
        case MOVE_UP -> moveNode(node, -1);
        case MOVE_DOWN -> moveNode(node, 1);
    }
}
```

新增 `selectNode`：

```java
private void selectNode(TraceNode node) {
    selectedNodeId = node.id();
    controller.setFocusedNodeId(selectedNodeId);
}
```

- [ ] **步骤 6：把 selected 操作拆成按节点执行的方法**

将旧方法保留为委托，或直接替换调用路径：

```java
private void editNode(TraceNode existing) {
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

private void deleteNode(TraceNode node) {
    if (node == null || !confirmNodeDelete.test(node)) {
        return;
    }
    selectedNodeId = null;
    controller.clearFocusedNodeId();
    controller.deleteNode(node.id());
    rebuildView();
}

private void moveNode(TraceNode node, int offset) {
    if (node == null) {
        return;
    }
    selectedNodeId = node.id();
    controller.moveNode(node.id(), offset);
    rebuildView();
}
```

删除旧的 `editSelectedNode()`、`deleteSelectedNode()`、`moveSelectedNode(int offset)` 私有方法；它们不再有调用者，所有 Edit/Delete/Move 入口都通过 `handleRowAction()` 按行节点执行。

- [ ] **步骤 7：运行测试验证通过**

运行：

```powershell
.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.CodeTracePanelRowActionTest --tests com.zimaai.codetrace.toolwindow.CodeTracePanelNavigationTest
```

预期：PASS。

- [ ] **步骤 8：Commit**

```powershell
git add src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelRowActionTest.java src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelNavigationTest.java
git commit -m "feat: wire node row actions"
```

---

## 任务 5：实现链接源状态文案、启用态和源节点视觉提示

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java` 内部 `NodeNameRenderer`
- 测试：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelLinkSourceStatusTest.java`

- [ ] **步骤 1：编写失败的链接源状态测试**

创建 `CodeTracePanelLinkSourceStatusTest`：

```java
package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.storage.TraceJsonMapper;
import com.zimaai.codetrace.storage.TraceStorageService;
import java.awt.Component;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import javax.swing.JLabel;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeTracePanelLinkSourceStatusTest {
    @TempDir
    Path tempDir;

    @Test
    void showsGuidanceAndDisablesLinkToHereWhenNoSourceIsSet() {
        CodeTracePanel panel = panelFor(documentWithTwoNodes());
        panel.editorPanel().nodeTable().setRowSelectionInterval(0, 0);

        assertFalse(panel.editorPanel().linkToHereButton().isEnabled());
        assertTrue(panel.editorPanel().linkStatus().getText().contains("链接源：未设置"));
        assertTrue(panel.editorPanel().linkStatus().getText().contains("Set as Source"));
    }

    @Test
    void settingSourceShowsNumberAndNodeName() {
        CodeTracePanel panel = panelFor(documentWithTwoNodes());
        panel.editorPanel().nodeTable().setRowSelectionInterval(0, 0);

        panel.editorPanel().setAsSourceButton().doClick();

        assertTrue(panel.editorPanel().linkToHereButton().isEnabled());
        assertTrue(panel.editorPanel().linkStatus().getText().contains("链接源：#1 first"));
    }

    @Test
    void pendingSourceRendererShowsLeftStripe() {
        CodeTracePanel panel = panelFor(documentWithTwoNodes());
        JTable table = panel.editorPanel().nodeTable();
        table.setRowSelectionInterval(0, 0);
        panel.editorPanel().setAsSourceButton().doClick();

        Component component = table.getColumnModel().getColumn(1).getCellRenderer()
                .getTableCellRendererComponent(table, table.getValueAt(0, 1), false, false, 0, 1);

        assertTrue(((JLabel) component).getBorder().toString().contains("Compound"));
    }

    @Test
    void deletingSourceClearsLinkSourceState() {
        CodeTracePanel panel = panelFor(documentWithTwoNodes());
        panel.setConfirmNodeDeleteForTest(node -> true);
        panel.editorPanel().nodeTable().setRowSelectionInterval(0, 0);
        panel.editorPanel().setAsSourceButton().doClick();

        clickRowAction(panel, 0, NodeRowAction.DELETE);

        assertFalse(panel.editorPanel().linkToHereButton().isEnabled());
        assertTrue(panel.editorPanel().linkStatus().getText().contains("链接源：未设置"));
    }

    private CodeTracePanel panelFor(TraceDocument document) {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-1.json", document);
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        CodeTracePanel panel = new CodeTracePanel(controller);
        panel.reloadFromDisk();
        return panel;
    }

    private static void clickRowAction(CodeTracePanel panel, int row, NodeRowAction action) {
        JTable table = panel.editorPanel().nodeTable();
        Object value = table.getValueAt(row, 3);
        Component component = table.getColumnModel().getColumn(3).getCellEditor()
                .getTableCellEditorComponent(table, value, true, row, 3);
        ((NodeRowActionsPanel) component).button(action).doClick();
    }

    private static TraceDocument documentWithTwoNodes() {
        return new TraceDocument(
                3,
                "trace-1",
                "Trace 1",
                "",
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(
                        new TraceNode("node-1", "first", "A#a", "a()", "A.java", 10, "JAVA", "", "A#a"),
                        new TraceNode("node-2", "second", "B#b", "b()", "B.java", 20, "JAVA", "", "B#b")),
                List.of(),
                Set.of());
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```powershell
.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.CodeTracePanelLinkSourceStatusTest
```

预期：FAIL。失败点应包含旧文案 `Link source: none` 或 renderer 没有源节点边框。

- [ ] **步骤 3：增加编号缓存和状态栏文案方法**

在 `CodeTracePanel` 增加字段：

```java
private Map<String, String> currentNumberMap = Map.of();
```

在 `rebuildView()` 计算编号后赋值：

```java
currentNumberMap = numberMap;
```

在无文档分支清空：

```java
currentNumberMap = Map.of();
updateLinkStatus();
```

新增：

```java
private void updateLinkStatus() {
    String pendingSourceId = controller.state().pendingLinkSourceId();
    TraceNode source = findNodeById(pendingSourceId);
    if (source == null) {
        editorPanel.linkStatus().setText("链接源：未设置，请先选中节点并点击 Set as Source");
        return;
    }
    String number = currentNumberMap.getOrDefault(source.id(), "?");
    editorPanel.linkStatus().setText("链接源：#" + number + " " + source.displayName());
}
```

将 `rebuildView()` 末尾旧文案：

```java
editorPanel.linkStatus().setText("Link source: " + ...);
```

替换为：

```java
updateLinkStatus();
```

- [ ] **步骤 4：清理失效 pending source**

在 `rebuildView()` 当前文档存在时，计算节点 ID 集合后检查：

```java
if (controller.state().pendingLinkSourceId() != null
        && document.nodes().stream().noneMatch(node -> node.id().equals(controller.state().pendingLinkSourceId()))) {
    controller.state().clearPendingLinkSource();
}
```

如果 `CodeTraceState.clearPendingLinkSource()` 不是 public，优先在 `CodeTraceController` 增加：

```java
public void clearPendingLinkSource() {
    state.clearPendingLinkSource();
}
```

然后在 `CodeTracePanel` 调用 `controller.clearPendingLinkSource()`。

- [ ] **步骤 5：源节点视觉提示**

在内部 `NodeNameRenderer.getTableCellRendererComponent()` 中，现有 `pendingSourceSupplier` 已经注入。设置边框：

```java
boolean isPendingSource = node.id().equals(pendingSourceSupplier.get());
javax.swing.border.Border padding = javax.swing.BorderFactory.createEmptyBorder(3, 4, 3, 4);
if (isPendingSource) {
    javax.swing.border.Border stripe = javax.swing.BorderFactory.createMatteBorder(
            0, 4, 0, 0, new com.intellij.ui.JBColor(0x3D7DCC, 0x4C8EDA));
    label.setBorder(javax.swing.BorderFactory.createCompoundBorder(stripe, padding));
} else {
    label.setBorder(padding);
}
```

保持 selected 行仍由 JTable selection foreground/background 控制。

- [ ] **步骤 6：运行测试验证通过**

运行：

```powershell
.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.CodeTracePanelLinkSourceStatusTest --tests com.zimaai.codetrace.toolwindow.CodeTracePanelNavigationTest
```

预期：PASS。

- [ ] **步骤 7：Commit**

```powershell
git add src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelLinkSourceStatusTest.java src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelNavigationTest.java
git commit -m "feat: clarify link source state"
```

---

## 任务 6：状态栏操作反馈

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
- 测试：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelLinkSourceStatusTest.java`

- [ ] **步骤 1：编写失败的操作反馈测试**

在 `CodeTracePanelLinkSourceStatusTest` 增加：

```java
@Test
void saveTraceNoteShowsFeedback() {
    CodeTracePanel panel = panelFor(documentWithTwoNodes());

    panel.editorPanel().traceNote().setText("updated");
    panel.editorPanel().saveTraceNoteButton().doClick();

    assertTrue(panel.editorPanel().linkStatus().getText().contains("Trace Note 已保存"));
}

@Test
void moveRowActionShowsFeedback() {
    CodeTracePanel panel = panelFor(documentWithTwoNodes());

    clickRowAction(panel, 0, NodeRowAction.MOVE_DOWN);

    assertTrue(panel.editorPanel().linkStatus().getText().contains("节点已下移"));
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```powershell
.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.CodeTracePanelLinkSourceStatusTest
```

预期：FAIL。失败点为状态栏仍显示链接源文案。

- [ ] **步骤 3：增加反馈方法并在操作后调用**

在 `CodeTracePanel` 增加：

```java
private void showFeedback(String message) {
    editorPanel.linkStatus().setText(message);
}
```

在操作完成后调用：

```java
private void saveTraceNote() {
    if (controller.state().currentDocument() == null) {
        return;
    }
    controller.saveDescription(editorPanel.traceNote().getText());
    rebuildView();
    showFeedback("Trace Note 已保存");
}
```

```java
private void saveNodeNote() {
    if (selectedNodeId == null) {
        return;
    }
    controller.saveNodeNote(selectedNodeId, editorPanel.nodeNote().getText());
    rebuildView();
    showFeedback("Node Note 已保存");
}
```

在 `moveNode()`：

```java
controller.moveNode(node.id(), offset);
rebuildView();
showFeedback(offset < 0 ? "节点已上移" : "节点已下移");
```

在 `deleteNode()`：

```java
controller.deleteNode(node.id());
rebuildView();
showFeedback("节点已删除");
```

在 `linkToSelectedNode()` 成功路径：

```java
controller.linkPendingSourceTo(selectedNodeId, TraceLinkKind.MANUAL);
rebuildView();
showFeedback("链接已创建");
```

在 `unlinkSelectedNode()`：

```java
controller.unlinkNode(selectedNodeId);
rebuildView();
showFeedback("链接已取消");
```

`setSelectedAsSource()` 不使用反馈覆盖，应继续调用 `rebuildView()` 后显示 `链接源：#...`。

- [ ] **步骤 4：运行测试验证通过**

运行：

```powershell
.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.CodeTracePanelLinkSourceStatusTest
```

预期：PASS。

- [ ] **步骤 5：Commit**

```powershell
git add src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelLinkSourceStatusTest.java
git commit -m "feat: show trace operation feedback"
```

---

## 任务 7：适配四列表格列宽逻辑

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
- 测试：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelColumnWidthTest.java`

- [ ] **步骤 1：更新列宽测试为四列**

在 `CodeTracePanelColumnWidthTest.keepsUserAdjustedColumnWidthsAfterCollapseAll()` 中：

```java
int beforeActionWidth = columns.getColumn(3).getWidth();
...
assertEquals(beforeNumberWidth, savedColumnWidths(panel)[0]);
assertEquals(beforeNameWidth, savedColumnWidths(panel)[1]);
assertEquals(beforeNumberWidth, table.getColumnModel().getColumn(0).getWidth());
assertEquals(beforeNameWidth, table.getColumnModel().getColumn(1).getWidth());
assertEquals(beforeActionWidth, table.getColumnModel().getColumn(3).getWidth());
assertEquals(
        viewportWidth - beforeNumberWidth - beforeNameWidth - beforeActionWidth,
        table.getColumnModel().getColumn(2).getWidth());
```

在 `stretchesLastColumnToFillViewportWidth()` 改名为：

```java
void stretchesLinkColumnToFillViewportWidthWhileActionColumnStaysFixed()
```

并断言：

```java
int actionWidth = columns.getColumn(3).getWidth();
int actualTotalWidth = columns.getColumn(0).getWidth()
        + columns.getColumn(1).getWidth()
        + columns.getColumn(2).getWidth()
        + actionWidth;

assertEquals(viewportWidth, actualTotalWidth);
assertFalse(columns.getColumn(2).getResizable());
assertFalse(columns.getColumn(3).getResizable());
assertEquals(actionWidth, columns.getColumn(3).getPreferredWidth());
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```powershell
.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.CodeTracePanelColumnWidthTest
```

预期：FAIL。失败点应为旧代码只处理 3 列，或 action column 被拉伸。

- [ ] **步骤 3：更新默认宽度和固定操作列**

在 `rebuildView()` 列配置处增加：

```java
int actionWidth = 112;
editorPanel.nodeTable().getColumnModel().getColumn(3).setMinWidth(actionWidth);
editorPanel.nodeTable().getColumnModel().getColumn(3).setMaxWidth(actionWidth);
editorPanel.nodeTable().getColumnModel().getColumn(3).setPreferredWidth(actionWidth);
editorPanel.nodeTable().getColumnModel().getColumn(3).setWidth(actionWidth);
```

首次默认宽度从 7 单位改为扣除 action 后分配：

```java
int actionWidth = 112;
int totalWidth = editorPanel.nodeTable().getWidth();
if (totalWidth <= 0) {
    totalWidth = 560;
}
int flexibleWidth = Math.max(420, totalWidth - actionWidth);
int unit = flexibleWidth / 7;
setColumnWidth(0, unit);
setColumnWidth(1, unit * 5);
setColumnWidth(3, actionWidth);
columnWidthsInitialized = true;
saveColumnWidths();
stretchLastColumnToViewport();
```

新增 helper：

```java
private void setColumnWidth(int index, int width) {
    editorPanel.nodeTable().getColumnModel().getColumn(index).setPreferredWidth(width);
    editorPanel.nodeTable().getColumnModel().getColumn(index).setWidth(width);
}
```

- [ ] **步骤 4：更新保存和恢复逻辑**

`saveColumnWidths()` 继续保存用户关心的 `编号`、`节点名称` 两列：

```java
if (editorPanel.nodeTable().getColumnModel().getColumnCount() >= 4) {
    savedColumnWidths = new int[] {
        editorPanel.nodeTable().getColumnModel().getColumn(0).getWidth(),
        editorPanel.nodeTable().getColumnModel().getColumn(1).getWidth()
    };
}
```

`restoreColumnWidths()` 继续恢复前两列，然后调用 `stretchLastColumnToViewport()`。

`stretchLastColumnToViewport()` 改为操作四列，并让 `链接关系` 列吸收剩余宽度：

```java
if (table.getColumnModel().getColumnCount() < 4) {
    return;
}
int numberWidth = table.getColumnModel().getColumn(0).getWidth();
int nameWidth = table.getColumnModel().getColumn(1).getWidth();
int actionWidth = table.getColumnModel().getColumn(3).getWidth();
int linkWidth = Math.max(0, viewportWidth - numberWidth - nameWidth - actionWidth);

adjustingFlexibleColumn = true;
try {
    table.getColumnModel().getColumn(2).setWidth(linkWidth);
    table.getColumnModel().getColumn(2).setPreferredWidth(linkWidth);
} finally {
    adjustingFlexibleColumn = false;
}
```

Keep the method name if minimizing churn; add a comment that the flexible column is now `链接关系`, because `操作` is fixed.

- [ ] **步骤 5：运行测试验证通过**

运行：

```powershell
.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.CodeTracePanelColumnWidthTest
```

预期：PASS。

- [ ] **步骤 6：Commit**

```powershell
git add src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelColumnWidthTest.java
git commit -m "fix: preserve column widths with row actions"
```

---

## 任务 8：回归顶部工具栏、按钮状态和全量测试

**文件：**
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTest.java`
- 验证：全量测试

- [ ] **步骤 1：更新顶部工具栏测试**

在 `CodeTracePanelTest.keepsRefreshAndFileListToggleInTopToolbar()` 中保留：

```java
assertEquals(List.of("Refresh", "Toggle Files"), CodeTracePanel.topToolbarButtonLabels());
```

继续断言以下按钮不在顶层工具栏：

```java
assertNull(findTopToolbarLabel("Save Trace Note"));
assertNull(findTopToolbarLabel("Save Node Note"));
assertNull(findTopToolbarLabel("Set as Source"));
assertNull(findTopToolbarLabel("Link To Here"));
assertNull(findTopToolbarLabel("Unlink"));
assertNull(findTopToolbarLabel("Go to Linked Node"));
```

新增或更新测试，确认旧行操作按钮不在中间主工具栏：

```java
@Test
void rowActionsAreNotInNodeToolbar() {
    TraceEditorPanel editorPanel = new TraceEditorPanel();

    assertFalse(toolbarContainsButtonText(editorPanel.nodeToolbar(), "Edit Node"));
    assertFalse(toolbarContainsButtonText(editorPanel.nodeToolbar(), "Delete Node"));
    assertFalse(toolbarContainsButtonText(editorPanel.nodeToolbar(), "Move Up"));
    assertFalse(toolbarContainsButtonText(editorPanel.nodeToolbar(), "Move Down"));
}
```

如果 helper 已在 `TraceEditorPanelTest` 中存在，不跨测试类复用；在 `CodeTracePanelTest` 内新增同名 private helper。

- [ ] **步骤 2：运行 toolwindow 测试**

运行：

```powershell
.\gradlew.bat test --tests com.zimaai.codetrace.toolwindow.*
```

预期：PASS。失败时按报错更新对应测试：旧按钮 getter 断言改由 `NodeRowActionsPanelTest` 覆盖，旧 `Go To Linked` 文案断言改为 `Go to Linked Node`。

- [ ] **步骤 3：运行全量测试**

运行：

```powershell
.\gradlew.bat test
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 4：检查工作区和提交**

运行：

```powershell
git status --short
```

预期：仅显示本任务更新的测试文件；如果还有前序任务遗漏文件，检查原因后一起提交。

提交：

```powershell
git add src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTest.java
git commit -m "test: cover optimized tool window layout"
```

---

## 任务 9：最终验收

**文件：**
- 验证：全量测试和工作区状态

- [ ] **步骤 1：确认规格状态已批准**

运行：

```powershell
rg -n "状态" docs/superpowers/specs/2026-06-08-code-trace-ui-layout-optimization-design.md
```

预期输出包含：

```text
**状态**: 已批准
```

- [ ] **步骤 2：运行最终验证**

运行：

```powershell
.\gradlew.bat test
git status --short
```

预期：

- Gradle 输出 `BUILD SUCCESSFUL`。
- `git status --short` 没有未提交源码或测试文件。

- [ ] **步骤 3：确认工作区干净**

运行：

```powershell
git status --short
```

预期：无输出。所有实现任务已在各自任务末尾提交。

---

## 自检清单

- 规格 3.1：左侧保留 `TraceFileListPanel`，计划没有实现节点树。
- 规格 3.2：中间节点主工作区由 `TraceEditorPanel.nodeMainPanel` 承载。
- 规格 3.3：右侧备注侧栏由 `TraceEditorPanel.notesPanel` 承载。
- 规格 4：节点工具栏只保留链接组和视图组；行操作移入表格 `操作` 列。
- 规格 5：表格列变为 `编号`、`节点名称`、`链接关系`、`操作`；列宽测试覆盖固定操作列。
- 规格 6：`Link To Here` 启用态、链接源文案、源节点视觉提示都有测试。
- 规格 7：状态栏反馈覆盖保存、移动，并在实现中覆盖链接、取消链接、删除。
- 规格 8：组件边界保持在 `toolwindow` 包；未改 trace JSON schema。
- 非目标检查：计划没有右键菜单、排序、节点树、批量删除/移动或独立 toast 系统。
