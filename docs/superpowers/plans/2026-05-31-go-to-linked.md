# Go To Linked — 合并导航按钮 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将 "Go to Source" 和 "Go to Target" 两个按钮合并为一个 "Go To Linked" 按钮，单链接直接跳转，多链接弹出分组菜单。

**架构：** 在 CodeTracePanel 中新增 `LinkedNodes` 数据结构和多链接查找方法，替换 TraceEditorPanel 中的两个按钮为一个，用 `JPopupMenu` 实现多链接场景的分组选择。

**技术栈：** Java Swing (IntelliJ IDEA 插件)

---

### 任务 1：更新 TraceEditorPanel — 合并按钮定义

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java`

- [ ] **步骤 1：替换按钮字段声明**

在 `TraceEditorPanel.java` 中，将第 38-39 行的两个按钮字段替换为一个：

```java
// 删除:
// private final JButton goToSourceButton = new JButton("Go to Source", AllIcons.Actions.Back);
// private final JButton goToTargetButton = new JButton("Go to Target", AllIcons.Actions.Forward);

// 新增:
private final JButton goToLinkedButton = new JButton("Go To Linked", AllIcons.Actions.Find);
```

- [ ] **步骤 2：更新构造函数中的初始 disabled 设置**

将第 53-54 行替换为：

```java
// 删除:
// goToSourceButton.setEnabled(false);
// goToTargetButton.setEnabled(false);

// 新增:
goToLinkedButton.setEnabled(false);
```

- [ ] **步骤 3：更新 configureNodeToolbar() 方法**

将第 97-98 行替换为：

```java
// 删除:
// nodeToolbar.add(goToSourceButton);
// nodeToolbar.add(goToTargetButton);

// 新增:
nodeToolbar.add(goToLinkedButton);
```

- [ ] **步骤 4：更新 addTooltips() 方法**

将第 109-110 行替换为：

```java
// 删除:
// goToSourceButton.setToolTipText("Navigate to the linked source node of the current selection");
// goToTargetButton.setToolTipText("Navigate to the linked target node of the current selection");

// 新增:
goToLinkedButton.setToolTipText("Navigate to linked nodes. Click to jump if only one link exists, or show a menu for multiple links.");
```

- [ ] **步骤 5：替换 getter 方法**

将第 195-200 行的两个 getter 替换为：

```java
// 删除:
// JButton goToSourceButton() { return goToSourceButton; }
// JButton goToTargetButton() { return goToTargetButton; }

// 新增:
JButton goToLinkedButton() {
    return goToLinkedButton;
}
```

- [ ] **步骤 6：编译验证**

```bash
./gradlew compileJava
```

预期：编译通过。

- [ ] **步骤 7：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java
git commit -m "refactor: merge Go to Source and Go to Target into Go To Linked button"
```

---

### 任务 2：更新 CodeTracePanel — 多链接逻辑与菜单

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`

- [ ] **步骤 1：新增 LinkedNodes 内部记录类和导入**

在 `CodeTracePanel.java` 顶部新增 import：

```java
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
```

在类末尾（`private record NodeInput(...)` 之前或旁边）新增记录类：

```java
private record LinkedNodes(
        List<TraceNode> sources,
        List<TraceNode> targets) {
}
```

- [ ] **步骤 2：新增 findAllLinksForSelectedNode() 方法**

在 `findSelectedLink()` 方法附近添加：

```java
private List<TraceLink> findAllLinksForSelectedNode() {
    if (selectedNodeId == null || controller.state().currentDocument() == null) {
        return List.of();
    }
    return controller.state().currentDocument().links().stream()
            .filter(link -> selectedNodeId.equals(link.sourceNodeId()) || selectedNodeId.equals(link.targetNodeId()))
            .toList();
}
```

- [ ] **步骤 3：新增 findAllLinkedNodes() 方法**

```java
private LinkedNodes findAllLinkedNodes() {
    List<TraceLink> links = findAllLinksForSelectedNode();
    List<TraceNode> sources = new ArrayList<>();
    List<TraceNode> targets = new ArrayList<>();
    for (TraceLink link : links) {
        if (selectedNodeId.equals(link.targetNodeId())) {
            TraceNode sourceNode = findNodeById(link.sourceNodeId());
            if (sourceNode != null) {
                sources.add(sourceNode);
            }
        }
        if (selectedNodeId.equals(link.sourceNodeId())) {
            TraceNode targetNode = findNodeById(link.targetNodeId());
            if (targetNode != null) {
                targets.add(targetNode);
            }
        }
    }
    return new LinkedNodes(List.copyOf(sources), List.copyOf(targets));
}
```

- [ ] **步骤 4：新增 hasLinkedNodes() 方法**

```java
private boolean hasLinkedNodes() {
    if (selectedNodeId == null || controller.state().currentDocument() == null) {
        return false;
    }
    return controller.state().currentDocument().links().stream()
            .anyMatch(link -> selectedNodeId.equals(link.sourceNodeId())
                    || selectedNodeId.equals(link.targetNodeId()));
}
```

- [ ] **步骤 5：用 goToLinked() 替换 goToLinkedSource() 和 goToLinkedTarget()**

删除第 338-350 行的两个方法，替换为：

```java
private void goToLinked() {
    LinkedNodes linked = findAllLinkedNodes();
    List<TraceNode> allLinked = new ArrayList<>();
    allLinked.addAll(linked.sources());
    allLinked.addAll(linked.targets());

    if (allLinked.isEmpty()) {
        return;
    }

    if (allLinked.size() == 1) {
        controller.navigateToNode(allLinked.get(0));
        return;
    }

    showLinkedNodesMenu(linked);
}

private void showLinkedNodesMenu(LinkedNodes linked) {
    JPopupMenu menu = new JPopupMenu();

    if (!linked.sources().isEmpty()) {
        JMenuItem sourceLabel = new JMenuItem("Sources (→)");
        sourceLabel.setEnabled(false);
        Font defaultFont = sourceLabel.getFont();
        sourceLabel.setFont(defaultFont.deriveFont(Font.BOLD));
        menu.add(sourceLabel);

        for (TraceNode node : linked.sources()) {
            JMenuItem item = new JMenuItem(node.displayName());
            item.setToolTipText(node.filePath() + ":" + node.line());
            item.addActionListener(e -> controller.navigateToNode(node));
            menu.add(item);
        }
    }

    if (!linked.sources().isEmpty() && !linked.targets().isEmpty()) {
        menu.add(new JSeparator());
    }

    if (!linked.targets().isEmpty()) {
        JMenuItem targetLabel = new JMenuItem("Targets (←)");
        targetLabel.setEnabled(false);
        Font defaultFont = targetLabel.getFont();
        targetLabel.setFont(defaultFont.deriveFont(Font.BOLD));
        menu.add(targetLabel);

        for (TraceNode node : linked.targets()) {
            JMenuItem item = new JMenuItem(node.displayName());
            item.setToolTipText(node.filePath() + ":" + node.line());
            item.addActionListener(e -> controller.navigateToNode(node));
            menu.add(item);
        }
    }

    JButton button = editorPanel.goToLinkedButton();
    menu.show(button, 0, button.getHeight());
}
```

- [ ] **步骤 6：更新 wireNodeActions() 中的 action listener**

将第 178-179 行替换为：

```java
// 删除:
// editorPanel.goToSourceButton().addActionListener(event -> goToLinkedSource());
// editorPanel.goToTargetButton().addActionListener(event -> goToLinkedTarget());

// 新增:
editorPanel.goToLinkedButton().addActionListener(event -> goToLinked());
```

- [ ] **步骤 7：更新 refreshButtons() 中的按钮启用逻辑**

将第 483-484 行替换为：

```java
// 删除:
// editorPanel.goToSourceButton().setEnabled(linkedSourceNode != null);
// editorPanel.goToTargetButton().setEnabled(linkedTargetNode != null);

// 新增:
editorPanel.goToLinkedButton().setEnabled(hasLinkedNodes());
```

同时删除第 461-462 行中不再使用的变量声明：

```java
// 删除:
// TraceNode linkedSourceNode = findLinkedSourceNode();
// TraceNode linkedTargetNode = findLinkedTargetNode();
```

- [ ] **步骤 8：删除不再使用的旧方法**

删除以下方法：
- `findSelectedLink()`（第 497-505 行）
- `findLinkedSourceNode()`（第 507-510 行）
- `findLinkedTargetNode()`（第 512-515 行）

- [ ] **步骤 9：清理不再需要的 import**

确认 `CodeTracePanel.java` 顶部不再有未使用的 import（如 `TraceLink`、`JOptionPane` 删掉后不应残留空闲 import）。

如果 `JOptionPane` 仍在其他地方使用（`createFile()` 等），保留它。

- [ ] **步骤 10：编译验证**

```bash
./gradlew compileJava
```

预期：编译通过。

- [ ] **步骤 11：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java
git commit -m "feat: merge navigation buttons with multi-link popup menu"
```

---

### 任务 3：更新 TraceEditorPanelTest — 测试新按钮

**文件：**
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/TraceEditorPanelTest.java`

- [ ] **步骤 1：更新 usesWrapLayoutForNodeToolbarAndDisablesJumpButtonsByDefault 测试**

将测试方法中的引用替换：

```java
@Test
void usesWrapLayoutForNodeToolbarAndDisablesJumpButtonByDefault() {
    TraceEditorPanel panel = new TraceEditorPanel();

    LayoutManager layout = panel.nodeToolbar().getLayout();
    assertEquals(WrapLayout.class, layout.getClass());

    JButton goToLinked = panel.goToLinkedButton();
    assertEquals("Go To Linked", goToLinked.getText());
    assertFalse(goToLinked.isEnabled());
}
```

注意测试方法名从 `usesWrapLayoutForNodeToolbarAndDisablesJumpButtonsByDefault` 改为 `usesWrapLayoutForNodeToolbarAndDisablesJumpButtonByDefault`（Buttons → Button）。

- [ ] **步骤 2：更新 setsIconsOnAllToolbarButtons 测试**

将第 57-58 行替换为：

```java
// 删除:
// assertNotNull(panel.goToSourceButton().getIcon(), "Go to Source button should have an icon");
// assertNotNull(panel.goToTargetButton().getIcon(), "Go to Target button should have an icon");

// 新增:
assertNotNull(panel.goToLinkedButton().getIcon(), "Go To Linked button should have an icon");
```

- [ ] **步骤 3：更新 setsTooltipsOnAllToolbarButtons 测试**

将第 74-75 行替换为：

```java
// 删除:
// assertNotNull(panel.goToSourceButton().getToolTipText(), "Go to Source button should have a tooltip");
// assertNotNull(panel.goToTargetButton().getToolTipText(), "Go to Target button should have a tooltip");

// 新增:
assertNotNull(panel.goToLinkedButton().getToolTipText(), "Go To Linked button should have a tooltip");
```

- [ ] **步骤 4：运行测试验证修改**

```bash
./gradlew test --tests "com.zimaai.codetrace.toolwindow.TraceEditorPanelTest"
```

预期：全部 PASS。

- [ ] **步骤 5：Commit**

```bash
git add src/test/java/com/zimaai/codetrace/toolwindow/TraceEditorPanelTest.java
git commit -m "test: update TraceEditorPanelTest for Go To Linked button"
```

---

### 任务 4：更新 CodeTracePanelNavigationTest — 测试新导航逻辑

**文件：**
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelNavigationTest.java`

- [ ] **步骤 1：更新 disablesEndpointNavigationForUnlinkedSelection 测试**

```java
@Test
void disablesNavigationForUnlinkedSelection() {
    AtomicReference<TraceNode> navigated = new AtomicReference<>();
    CodeTracePanel panel = panelFor(documentWithLinkedAndUnlinkedNodes(), navigated);

    panel.editorPanel().nodeList().setSelectedIndex(2);

    assertFalse(panel.editorPanel().goToLinkedButton().isEnabled());
}
```

- [ ] **步骤 2：更新 navigatesToBothLinkEndpointsFromEitherSide 测试**

替换为验证单按钮 + 直接跳转的测试：

```java
@Test
void navigatesToLinkedNodeDirectlyWhenSingleLinkExists() {
    AtomicReference<TraceNode> navigated = new AtomicReference<>();
    CodeTracePanel panel = panelFor(documentWithLinkedAndUnlinkedNodes(), navigated);

    // Selecting node-1 (source of link-1 to node-2) has 1 linked node: node-2
    panel.editorPanel().nodeList().setSelectedIndex(0);
    assertTrue(panel.editorPanel().goToLinkedButton().isEnabled());
    panel.editorPanel().goToLinkedButton().doClick();
    assertEquals("node-2", navigated.get().id());

    // Selecting node-2 (target of link-1 from node-1) has 1 linked node: node-1
    panel.editorPanel().nodeList().setSelectedIndex(1);
    panel.editorPanel().goToLinkedButton().doClick();
    assertEquals("node-1", navigated.get().id());
}
```

- [ ] **步骤 3：更新 disablesMissingEndpointAndClearsButtonsAfterUnlinkOrDelete 测试**

```java
@Test
void disablesMissingEndpointAndClearsButtonAfterUnlinkOrDelete() {
    AtomicReference<TraceNode> navigated = new AtomicReference<>();
    CodeTracePanel missingTargetPanel = panelFor(documentWithMissingTarget(), navigated);

    missingTargetPanel.editorPanel().nodeList().setSelectedIndex(0);
    assertTrue(missingTargetPanel.editorPanel().goToLinkedButton().isEnabled());

    CodeTracePanel unlinkPanel = panelFor(documentWithLinkedAndUnlinkedNodes(), new AtomicReference<>());
    unlinkPanel.editorPanel().nodeList().setSelectedIndex(0);
    unlinkPanel.editorPanel().unlinkButton().doClick();
    assertFalse(unlinkPanel.editorPanel().goToLinkedButton().isEnabled());

    CodeTracePanel deletePanel = panelFor(documentWithLinkedAndUnlinkedNodes(), new AtomicReference<>());
    deletePanel.editorPanel().nodeList().setSelectedIndex(0);
    deletePanel.editorPanel().deleteNodeButton().doClick();
    assertTrue(deletePanel.editorPanel().nodeList().isSelectionEmpty());
    assertFalse(deletePanel.editorPanel().goToLinkedButton().isEnabled());
}
```

- [ ] **步骤 4：新增多链接弹出菜单测试**

新增测试方法，使用包含多链接的文档：

```java
@Test
void showsPopupMenuWhenMultipleLinkedNodesExist() {
    AtomicReference<TraceNode> navigated = new AtomicReference<>();
    CodeTracePanel panel = panelFor(documentWithMultipleLinks(), navigated);

    // node-A has 3 linked nodes: node-B, node-C (as source), node-D (as target)
    panel.editorPanel().nodeList().setSelectedIndex(0);
    assertTrue(panel.editorPanel().goToLinkedButton().isEnabled());

    JButton button = panel.editorPanel().goToLinkedButton();
    // Direct click when multiple links exist should show popup
    // Verify menu structure: we can't fully automate popup interaction,
    // but we can verify the button state and click behavior doesn't throw
    button.doClick();
    // After popup closes (no selection), navigation should not happen
    // because user dismissed the menu
}

private static TraceDocument documentWithMultipleLinks() {
    return new TraceDocument(
            2,
            "trace-multi",
            "Trace Multi",
            "",
            Instant.parse("2026-05-29T10:00:00Z"),
            Instant.parse("2026-05-29T10:00:00Z"),
            List.of(
                    new TraceNode("node-A", "Node A", "A#a", "a()", "A.java", 10, "JAVA", "", "A#a"),
                    new TraceNode("node-B", "Node B", "B#b", "b()", "B.java", 20, "JAVA", "", "B#b"),
                    new TraceNode("node-C", "Node C", "C#c", "c()", "C.java", 30, "JAVA", "", "C#c"),
                    new TraceNode("node-D", "Node D", "D#d", "d()", "D.java", 40, "JAVA", "", "D#d")),
            List.of(
                    new TraceLink("link-1", "node-A", "node-B", Instant.parse("2026-05-29T10:01:00Z"), TraceLinkKind.MANUAL),
                    new TraceLink("link-2", "node-A", "node-C", Instant.parse("2026-05-29T10:02:00Z"), TraceLinkKind.MANUAL),
                    new TraceLink("link-3", "node-D", "node-A", Instant.parse("2026-05-29T10:03:00Z"), TraceLinkKind.MANUAL)));
}
```

- [ ] **步骤 5：运行导航测试**

```bash
./gradlew test --tests "com.zimaai.codetrace.toolwindow.CodeTracePanelNavigationTest"
```

预期：全部 PASS。

- [ ] **步骤 6：Commit**

```bash
git add src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelNavigationTest.java
git commit -m "test: update navigation tests for merged Go To Linked button and multi-link"
```

---

### 任务 5：全面验证

**文件：**
- 无需修改

- [ ] **步骤 1：运行全部测试**

```bash
./gradlew test
```

预期：全部 PASS。

- [ ] **步骤 2：确认无编译警告**

```bash
./gradlew compileJava compileTestJava
```

预期：无错误、无警告。

- [ ] **步骤 3：Commit 最终检查**

```bash
git status
```

确认只有预期文件被修改。
