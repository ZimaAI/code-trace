# Code Trace 工具栏精简与链接跳转实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 精简 `code-trace` Tool Window 顶部全局工具栏，只保留 `Refresh`，同时在节点区新增 `Go to Source / Go to Target`，并让节点区按钮在窄窗口下自动换行且不隐藏。

**架构：** `CodeTracePanel` 继续负责 Tool Window 级按钮编排和节点动作状态刷新，顶部工具栏只保留 `Refresh`。`TraceEditorPanel` 承担节点区工具条的结构调整与换行布局，新增显式跳转按钮；链接对端解析保持在 `CodeTracePanel` 内部，通过当前文档的 `links` 找到 source/target 节点，再复用现有 `CodeTraceController.navigateToNode(...)` 导航链路。

**技术栈：** Java 21、Swing、IntelliJ Platform SDK、JUnit 5、Gradle

---

## Planned File Structure

### Tool window layout and button ownership

- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/WrapLayout.java`
  - 为节点区按钮容器提供基于当前宽度计算高度的自动换行布局，避免窄窗口下按钮被裁掉。
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java`
  - 持有节点区工具条容器，新增 `Go to Source / Go to Target`，暴露测试所需的按钮和工具条访问器。
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
  - 顶部工具栏只保留 `Refresh`，保留 trace/node note 保存入口在编辑区，并为测试暴露 package-private 的 `editorPanel()`。

### Linked node navigation behavior

- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
  - 为跳转按钮接线、解析当前选中节点关联的 `TraceLink`、查找 source/target 对应的 `TraceNode`，并统一更新按钮启用状态。

### Tests and smoke checks

- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/TraceEditorPanelTest.java`
  - 锁定节点区工具条使用换行布局，并校验新增按钮存在。
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTest.java`
  - 从“字符串占位测试”改为真实实例化面板，验证顶部只剩 `Refresh`，其余按钮位于编辑区。
- 创建：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelNavigationTest.java`
  - 覆盖未链接节点禁用、已链接节点双向跳转、缺失对端禁用、`unlink/delete` 后状态刷新、双击当前节点导航不回归。
- 修改：`docs/superpowers/plans/2026-05-29-code-trace-manual-smoke-checklist.md`
  - 更新按钮可见性、窄窗口换行、显式 source/target 跳转的人工验证步骤。

## Task 1: 精简顶部工具栏并给节点区引入换行工具条

**文件：**
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/WrapLayout.java`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/TraceEditorPanelTest.java`
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTest.java`

- [ ] **步骤 1：先写失败的结构测试**

把 `src/test/java/com/zimaai/codetrace/toolwindow/TraceEditorPanelTest.java` 追加成：

```java
package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.intellij.ui.JBColor;
import org.junit.jupiter.api.Test;

class TraceEditorPanelTest {
    @Test
    void enablesSoftWrapForNodeNoteEditing() {
        TraceEditorPanel panel = new TraceEditorPanel();

        assertTrue(panel.nodeNote().getLineWrap());
        assertTrue(panel.nodeNote().getWrapStyleWord());
    }

    @Test
    void appliesThemeAwareSelectionColorsForNodeNote() {
        TraceEditorPanel panel = new TraceEditorPanel();

        assertEquals(JBColor.class, panel.nodeNote().getSelectionColor().getClass());
        assertEquals(JBColor.class, panel.nodeNote().getSelectedTextColor().getClass());
        assertEquals(JBColor.class, panel.nodeNote().getCaretColor().getClass());
    }

    @Test
    void usesWrappingToolbarAndExposesSourceTargetNavigationButtons() {
        TraceEditorPanel panel = new TraceEditorPanel();

        assertEquals(WrapLayout.class, panel.nodeToolbar().getLayout().getClass());
        assertEquals("Go to Source", panel.goToSourceButton().getText());
        assertEquals("Go to Target", panel.goToTargetButton().getText());
        assertFalse(panel.goToSourceButton().isEnabled());
        assertFalse(panel.goToTargetButton().isEnabled());
    }
}
```

把 `src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTest.java` 改成：

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
    void keepsOnlyRefreshInTopToolbarAndLeavesNodeActionsInEditorPane() {
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
        assertNull(panel.findButton("Save Trace Note"));
        assertNull(panel.findButton("Save Node Note"));
        assertNull(panel.findButton("Set as Source"));
        assertNull(panel.findButton("Link To Here"));
        assertNull(panel.findButton("Unlink"));
        assertNotNull(panel.editorPanel().saveTraceNoteButton());
        assertNotNull(panel.editorPanel().saveNodeNoteButton());
        assertNotNull(panel.editorPanel().setAsSourceButton());
        assertNotNull(panel.editorPanel().linkToHereButton());
        assertNotNull(panel.editorPanel().unlinkButton());
        assertNotNull(panel.editorPanel().goToSourceButton());
        assertNotNull(panel.editorPanel().goToTargetButton());
    }
}
```

- [ ] **步骤 2：运行测试确认它们失败**

运行：

```powershell
.\gradlew.bat test --tests "com.zimaai.codetrace.toolwindow.TraceEditorPanelTest" --tests "com.zimaai.codetrace.toolwindow.CodeTracePanelTest"
```

预期：

- `TraceEditorPanelTest` 编译失败，因为 `WrapLayout`、`nodeToolbar()`、`goToSourceButton()`、`goToTargetButton()` 还不存在
- `CodeTracePanelTest` 失败，因为顶部仍然有 `Save Trace Note / Save Node Note / Set as Source / Link To Here / Unlink`

- [ ] **步骤 3：实现最小布局改动**

创建 `src/main/java/com/zimaai/codetrace/toolwindow/WrapLayout.java`：

```java
package com.zimaai.codetrace.toolwindow;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

final class WrapLayout extends FlowLayout {
    WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= getHgap() + 1;
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int targetWidth = target.getWidth();
            if (targetWidth <= 0 && target.getParent() != null) {
                targetWidth = target.getParent().getWidth();
            }
            if (targetWidth <= 0) {
                targetWidth = Integer.MAX_VALUE;
            }

            Insets insets = target.getInsets();
            int horizontalInsetsAndGap = insets.left + insets.right + (getHgap() * 2);
            int maxWidth = targetWidth - horizontalInsetsAndGap;

            Dimension size = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            for (Component component : target.getComponents()) {
                if (!component.isVisible()) {
                    continue;
                }
                Dimension componentSize = preferred ? component.getPreferredSize() : component.getMinimumSize();
                if (rowWidth > 0 && rowWidth + getHgap() + componentSize.width > maxWidth) {
                    addRow(size, rowWidth, rowHeight);
                    rowWidth = 0;
                    rowHeight = 0;
                }
                if (rowWidth > 0) {
                    rowWidth += getHgap();
                }
                rowWidth += componentSize.width;
                rowHeight = Math.max(rowHeight, componentSize.height);
            }

            addRow(size, rowWidth, rowHeight);
            size.width += horizontalInsetsAndGap;
            size.height += insets.top + insets.bottom + (getVgap() * 2);

            if (SwingUtilities.getAncestorOfClass(JScrollPane.class, target) != null && target.isValid()) {
                size.width -= getHgap() + 1;
            }
            return size;
        }
    }

    private void addRow(Dimension size, int rowWidth, int rowHeight) {
        size.width = Math.max(size.width, rowWidth);
        if (size.height > 0) {
            size.height += getVgap();
        }
        size.height += rowHeight;
    }
}
```

把 `src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java` 改成下面这个结构：

```java
package com.zimaai.codetrace.toolwindow;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.UIUtil;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
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
    private final JButton goToSourceButton = new JButton("Go to Source");
    private final JButton goToTargetButton = new JButton("Go to Target");
    private final JLabel linkStatus = new JLabel("Link source: none");
    private final JPanel nodeToolbar = new JPanel(new WrapLayout(FlowLayout.LEADING, 8, 8));
    private final JPanel root = new JPanel(new BorderLayout());

    public TraceEditorPanel() {
        configureTextArea(traceNote);
        configureTextArea(nodeNote);
        goToSourceButton.setEnabled(false);
        goToTargetButton.setEnabled(false);

        JPanel traceNotePanel = new JPanel(new BorderLayout());
        traceNotePanel.add(new JBScrollPane(traceNote), BorderLayout.CENTER);
        traceNotePanel.add(saveTraceNoteButton, BorderLayout.SOUTH);

        nodeToolbar.add(editNodeButton);
        nodeToolbar.add(deleteNodeButton);
        nodeToolbar.add(moveUpButton);
        nodeToolbar.add(moveDownButton);
        nodeToolbar.add(setAsSourceButton);
        nodeToolbar.add(linkToHereButton);
        nodeToolbar.add(unlinkButton);
        nodeToolbar.add(goToSourceButton);
        nodeToolbar.add(goToTargetButton);

        JPanel nodeNotePanel = new JPanel(new BorderLayout());
        nodeNotePanel.add(new JBScrollPane(nodeNote), BorderLayout.CENTER);
        nodeNotePanel.add(saveNodeNoteButton, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JBScrollPane(nodeList), nodeNotePanel);
        split.setResizeWeight(0.7d);

        JPanel content = new JPanel(new BorderLayout());
        content.add(nodeToolbar, BorderLayout.NORTH);
        content.add(split, BorderLayout.CENTER);
        content.add(linkStatus, BorderLayout.SOUTH);

        root.add(traceNotePanel, BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
    }

    JPanel nodeToolbar() {
        return nodeToolbar;
    }

    public JButton goToSourceButton() {
        return goToSourceButton;
    }

    public JButton goToTargetButton() {
        return goToTargetButton;
    }
}
```

把 `src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java` 的 `configureLayout()` 改成只保留顶部刷新按钮，并加一个 package-private 访问器：

```java
    private void configureLayout() {
        JPanel toolbar = new JPanel();
        addButton(toolbar, "Refresh", this::refreshAndRepaint);

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

    TraceEditorPanel editorPanel() {
        return editorPanel;
    }
```

- [ ] **步骤 4：运行结构测试确认通过**

运行：

```powershell
.\gradlew.bat test --tests "com.zimaai.codetrace.toolwindow.TraceEditorPanelTest" --tests "com.zimaai.codetrace.toolwindow.CodeTracePanelTest"
```

预期：PASS。

- [ ] **步骤 5：Commit**

```powershell
git add src/main/java/com/zimaai/codetrace/toolwindow/WrapLayout.java src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java src/test/java/com/zimaai/codetrace/toolwindow/TraceEditorPanelTest.java src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTest.java
git commit -m "refactor(code-trace): simplify toolbar layout"
```

## Task 2: 为已链接节点增加显式 source/target 跳转

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
- 创建：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelNavigationTest.java`

- [ ] **步骤 1：先写失败的导航行为测试**

创建 `src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelNavigationTest.java`：

```java
package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.storage.TraceJsonMapper;
import com.zimaai.codetrace.storage.TraceStorageService;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeTracePanelNavigationTest {
    @TempDir
    Path tempDir;

    @Test
    void disablesEndpointNavigationForUnlinkedSelection() {
        AtomicReference<TraceNode> navigated = new AtomicReference<>();
        CodeTracePanel panel = panelFor(documentWithLinkedAndUnlinkedNodes(), navigated);

        panel.editorPanel().nodeList().setSelectedIndex(2);

        assertFalse(panel.editorPanel().goToSourceButton().isEnabled());
        assertFalse(panel.editorPanel().goToTargetButton().isEnabled());
    }

    @Test
    void navigatesToBothLinkEndpointsFromEitherSide() {
        AtomicReference<TraceNode> navigated = new AtomicReference<>();
        CodeTracePanel panel = panelFor(documentWithLinkedAndUnlinkedNodes(), navigated);

        panel.editorPanel().nodeList().setSelectedIndex(0);
        assertTrue(panel.editorPanel().goToSourceButton().isEnabled());
        assertTrue(panel.editorPanel().goToTargetButton().isEnabled());
        panel.editorPanel().goToSourceButton().doClick();
        assertEquals("node-1", navigated.get().id());
        panel.editorPanel().goToTargetButton().doClick();
        assertEquals("node-2", navigated.get().id());

        panel.editorPanel().nodeList().setSelectedIndex(1);
        panel.editorPanel().goToSourceButton().doClick();
        assertEquals("node-1", navigated.get().id());
        panel.editorPanel().goToTargetButton().doClick();
        assertEquals("node-2", navigated.get().id());
    }

    @Test
    void disablesMissingEndpointAndClearsButtonsAfterUnlinkOrDelete() {
        AtomicReference<TraceNode> navigated = new AtomicReference<>();
        CodeTracePanel missingTargetPanel = panelFor(documentWithMissingTarget(), navigated);

        missingTargetPanel.editorPanel().nodeList().setSelectedIndex(0);
        assertTrue(missingTargetPanel.editorPanel().goToSourceButton().isEnabled());
        assertFalse(missingTargetPanel.editorPanel().goToTargetButton().isEnabled());

        CodeTracePanel unlinkPanel = panelFor(documentWithLinkedAndUnlinkedNodes(), new AtomicReference<>());
        unlinkPanel.editorPanel().nodeList().setSelectedIndex(0);
        unlinkPanel.editorPanel().unlinkButton().doClick();
        assertFalse(unlinkPanel.editorPanel().goToSourceButton().isEnabled());
        assertFalse(unlinkPanel.editorPanel().goToTargetButton().isEnabled());

        CodeTracePanel deletePanel = panelFor(documentWithLinkedAndUnlinkedNodes(), new AtomicReference<>());
        deletePanel.editorPanel().nodeList().setSelectedIndex(0);
        deletePanel.editorPanel().deleteNodeButton().doClick();
        assertTrue(deletePanel.editorPanel().nodeList().isSelectionEmpty());
        assertFalse(deletePanel.editorPanel().goToSourceButton().isEnabled());
        assertFalse(deletePanel.editorPanel().goToTargetButton().isEnabled());
    }

    @Test
    void keepsDoubleClickNavigationForCurrentNode() {
        AtomicReference<TraceNode> navigated = new AtomicReference<>();
        CodeTracePanel panel = panelFor(documentWithLinkedAndUnlinkedNodes(), navigated);

        panel.editorPanel().nodeList().setSelectedIndex(1);

        MouseEvent event = new MouseEvent(
                panel.editorPanel().nodeList(),
                MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                10,
                10,
                2,
                false);
        for (MouseListener listener : panel.editorPanel().nodeList().getMouseListeners()) {
            listener.mouseClicked(event);
        }

        assertEquals("node-2", navigated.get().id());
    }

    private CodeTracePanel panelFor(TraceDocument document, AtomicReference<TraceNode> navigated) {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-1.json", document);
        CodeTraceController controller = new CodeTraceController(storage, node -> {
            navigated.set(node);
            return true;
        });
        CodeTracePanel panel = new CodeTracePanel(controller);
        panel.reloadFromDisk();
        return panel;
    }

    private static TraceDocument documentWithLinkedAndUnlinkedNodes() {
        return new TraceDocument(
                2,
                "trace-1",
                "Trace 1",
                "",
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(
                        new TraceNode("node-1", "source line", "A#a", "a()", "A.java", 10, "JAVA", "", "A#a"),
                        new TraceNode("node-2", "target line", "B#b", "b()", "B.java", 20, "JAVA", "", "B#b"),
                        new TraceNode("node-3", "standalone line", "C#c", "c()", "C.java", 30, "JAVA", "", "C#c")),
                List.of(new TraceLink("link-1", "node-1", "node-2", Instant.parse("2026-05-29T10:01:00Z"), TraceLinkKind.MANUAL)));
    }

    private static TraceDocument documentWithMissingTarget() {
        return new TraceDocument(
                2,
                "trace-2",
                "Trace 2",
                "",
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(new TraceNode("node-1", "source line", "A#a", "a()", "A.java", 10, "JAVA", "", "A#a")),
                List.of(new TraceLink("link-1", "node-1", "node-missing", Instant.parse("2026-05-29T10:01:00Z"), TraceLinkKind.MANUAL)));
    }
}
```

- [ ] **步骤 2：运行测试确认行为尚未实现**

运行：

```powershell
.\gradlew.bat test --tests "com.zimaai.codetrace.toolwindow.CodeTracePanelNavigationTest"
```

预期：

- `goToSourceButton / goToTargetButton` 状态断言失败，因为 `refreshButtons()` 还没有驱动它们
- 点击跳转按钮后 `navigated.get()` 仍为 `null`

- [ ] **步骤 3：实现最小导航逻辑**

把 `src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java` 增量修改为下面这个形态：

```java
package com.zimaai.codetrace.toolwindow;

import com.intellij.ui.JBSplitter;
import com.zimaai.codetrace.model.TraceLink;
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
    // 现有字段保持不变

    private void wireNodeActions() {
        editorPanel.editNodeButton().addActionListener(event -> editSelectedNode());
        editorPanel.deleteNodeButton().addActionListener(event -> deleteSelectedNode());
        editorPanel.moveUpButton().addActionListener(event -> moveSelectedNode(-1));
        editorPanel.moveDownButton().addActionListener(event -> moveSelectedNode(1));
        editorPanel.setAsSourceButton().addActionListener(event -> setSelectedAsSource());
        editorPanel.linkToHereButton().addActionListener(event -> linkToSelectedNode());
        editorPanel.unlinkButton().addActionListener(event -> unlinkSelectedNode());
        editorPanel.goToSourceButton().addActionListener(event -> goToLinkedSource());
        editorPanel.goToTargetButton().addActionListener(event -> goToLinkedTarget());
    }

    private void goToLinkedSource() {
        TraceNode sourceNode = findLinkedSourceNode();
        if (sourceNode != null) {
            controller.navigateToNode(sourceNode);
        }
    }

    private void goToLinkedTarget() {
        TraceNode targetNode = findLinkedTargetNode();
        if (targetNode != null) {
            controller.navigateToNode(targetNode);
        }
    }

    private TraceNode findLinkedSourceNode() {
        TraceLink link = findSelectedLink();
        return link == null ? null : findNodeById(link.sourceNodeId());
    }

    private TraceNode findLinkedTargetNode() {
        TraceLink link = findSelectedLink();
        return link == null ? null : findNodeById(link.targetNodeId());
    }

    private TraceLink findSelectedLink() {
        if (selectedNodeId == null || controller.state().currentDocument() == null) {
            return null;
        }
        return controller.state().currentDocument().links().stream()
                .filter(link -> selectedNodeId.equals(link.sourceNodeId()) || selectedNodeId.equals(link.targetNodeId()))
                .findFirst()
                .orElse(null);
    }

    private TraceNode findNodeById(String nodeId) {
        if (nodeId == null || controller.state().currentDocument() == null) {
            return null;
        }
        return controller.state().currentDocument().nodes().stream()
                .filter(node -> node.id().equals(nodeId))
                .findFirst()
                .orElse(null);
    }

    private void refreshButtons() {
        var document = controller.state().currentDocument();
        boolean hasDocument = document != null;
        boolean hasSelection = findSelectedNode() != null;
        boolean hasPendingSource = controller.state().pendingLinkSourceId() != null;
        TraceNode linkedSourceNode = findLinkedSourceNode();
        TraceNode linkedTargetNode = findLinkedTargetNode();

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
        editorPanel.goToSourceButton().setEnabled(linkedSourceNode != null);
        editorPanel.goToTargetButton().setEnabled(linkedTargetNode != null);
    }
}
```

- [ ] **步骤 4：运行导航测试确认通过**

运行：

```powershell
.\gradlew.bat test --tests "com.zimaai.codetrace.toolwindow.CodeTracePanelNavigationTest"
```

预期：PASS。

- [ ] **步骤 5：Commit**

```powershell
git add src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelNavigationTest.java
git commit -m "feat(code-trace): add link endpoint navigation"
```

## Task 3: 更新 smoke checklist 并完成回归验证

**文件：**
- 修改：`docs/superpowers/plans/2026-05-29-code-trace-manual-smoke-checklist.md`

- [ ] **步骤 1：更新人工验证清单**

把 `docs/superpowers/plans/2026-05-29-code-trace-manual-smoke-checklist.md` 中对应段落改成：

```md
## Tool Window Basics

1. `code-trace` is visible in the left tool window bar.
2. Open it and confirm visible controls:
   - Top buttons: `Refresh`
   - Left JSON file list
   - Right trace note, wrapped node toolbar, node list, node note
3. Confirm removed controls are not visible in the top toolbar:
   - `Save Trace Note`
   - `Save Node Note`
   - `Set as Source`
   - `Link To Here`
   - `Unlink`
4. Confirm node toolbar contains:
   - `Edit / Delete / Move Up / Move Down / Set as Source / Link To Here / Unlink / Go to Source / Go to Target`

## Nodes And Links

1. Every node row text is exactly one line of code (`displayName`).
2. Select one node and click `Set as Source`; the same node remains selected and link status shows source id.
3. Select another node and click `Link To Here`; source/target styling appears and the target node remains selected.
4. Click `Go to Source`; editor navigates to the linked source node code.
5. Click `Go to Target`; editor navigates to the linked target node code.
6. Click `Unlink`; linked styling is removed, the current node remains selected, and both jump buttons become disabled.
7. Use `Move Up` or `Move Down` on a selected node and confirm the same node remains selected after the list refreshes.
8. Delete the currently selected linked node and confirm the linked pair is removed, the node list ends with no selection, and both jump buttons are disabled.

## Responsive Layout

1. Narrow the tool window until the node toolbar cannot fit on one row.
2. Confirm the node toolbar wraps onto multiple lines.
3. Confirm no node toolbar button is hidden or moved into an overflow menu.
```

- [ ] **步骤 2：运行所有工具窗口相关测试**

运行：

```powershell
.\gradlew.bat test --tests "com.zimaai.codetrace.toolwindow.TraceEditorPanelTest" --tests "com.zimaai.codetrace.toolwindow.CodeTracePanelTest" --tests "com.zimaai.codetrace.toolwindow.CodeTracePanelNavigationTest" --tests "com.zimaai.codetrace.toolwindow.LinkedNodeListCellRendererTest" --tests "com.zimaai.codetrace.toolwindow.CodeTraceControllerTest"
```

预期：PASS。

- [ ] **步骤 3：运行完整回归**

运行：

```powershell
.\gradlew.bat test
```

预期：`BUILD SUCCESSFUL`

- [ ] **步骤 4：Commit**

```powershell
git add docs/superpowers/plans/2026-05-29-code-trace-manual-smoke-checklist.md
git commit -m "docs(code-trace): update toolbar navigation smoke checks"
```

## 自检

### 规格覆盖度

- 顶部全局工具栏只保留 `Refresh`：Task 1
- 节点区新增 `Go to Source / Go to Target`：Task 1、Task 2
- 已链接节点可跳到 source/target：Task 2
- 未链接或对端缺失时按钮置灰：Task 2
- 双击当前节点跳转不回归：Task 2
- 窄窗口按钮换行不隐藏：Task 1、Task 3
- 手工 smoke check 更新：Task 3

### 占位符扫描

- 本计划没有 `TODO`、`TBD`、`待定`、`后续实现` 之类占位符。
- 每个代码任务都给了明确的测试类、命令、预期失败或成功结果。

### 类型一致性

- 新增布局类统一命名为 `WrapLayout`
- 新增按钮统一命名为 `goToSourceButton` 与 `goToTargetButton`
- `CodeTracePanel` 内部辅助方法统一围绕 `TraceLink`、`TraceNode`、`navigateToNode(...)`

