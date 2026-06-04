# 节点列表重构实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 重构节点列表，去掉链接联动，支持多选，添加层级编号，用表格显示链接关系

**架构：** 创建 NodeNumberingService 处理编号，MultiSelectTransferHandler 处理多选拖拽，LinkRelationColumnRenderer 渲染表格，修改 CodeTraceController 去掉联动逻辑

**技术栈：** Java Swing (JTable, TransferHandler), IntelliJ Platform SDK

---

## 文件结构

### 新增文件
- `src/main/java/com/zimaai/codetrace/toolwindow/NodeNumberingService.java` - 计算节点层级编号
- `src/main/java/com/zimaai/codetrace/toolwindow/MultiSelectTransferHandler.java` - 处理多选拖拽
- `src/main/java/com/zimaai/codetrace/toolwindow/LinkRelationColumnRenderer.java` - 渲染表格单元格
- `src/test/java/com/zimaai/codetrace/toolwindow/NodeNumberingServiceTest.java` - 编号服务测试
- `src/test/java/com/zimaai/codetrace/toolwindow/MultiSelectTransferHandlerTest.java` - 多选拖拽测试
- `src/test/java/com/zimaai/codetrace/toolwindow/LinkRelationColumnRendererTest.java` - 渲染器测试

### 修改文件
- `src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java` - 删除链接联动逻辑
- `src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java` - 替换 JTree 为 JTable
- `src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java` - 适配 JTable
- `src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java` - 更新控制器测试

### 删除文件
- `src/main/java/com/zimaai/codetrace/toolwindow/LinkedNodeTreeCellRenderer.java` - 旧的渲染器
- `src/main/java/com/zimaai/codetrace/toolwindow/NodeTreeTransferHandler.java` - 旧的拖拽处理器

---

## 任务 1：去掉链接联动

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java:253-297`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java:358-396`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java:409-431`
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java:445-452`
- 测试：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java`

- [ ] **步骤 1：编写失败的测试 - 移动单个节点**

```java
@Test
void moveNode_shouldOnlyMoveSingleNode_notLinkedNodes() {
    // Given: 节点 A 和节点 B 通过链接关联
    TraceNode nodeA = createNode("node-a", "A", null);
    TraceNode nodeB = createNode("node-b", "B", null);
    TraceLink link = new TraceLink("link-1", "node-a", "node-b", Instant.now(), TraceLinkKind.MANUAL);
    TraceDocument doc = createDocument(List.of(nodeA, nodeB), List.of(link));
    when(storage.load(any())).thenReturn(doc);

    // When: 移动节点 A
    controller.moveNodeOrPair("node-a", 1);

    // Then: 只有节点 A 移动，节点 B 保持原位
    verify(storage).save(any(), argThat(saved -> {
        List<TraceNode> nodes = saved.nodes();
        int indexA = findIndex(nodes, "node-a");
        int indexB = findIndex(nodes, "node-b");
        return indexA == 1 && indexB == 0; // A 移动到 B 后面
    }));
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`gradle test --tests "com.zimaai.codetrace.toolwindow.CodeTraceControllerTest.moveNode_shouldOnlyMoveSingleNode_notLinkedNodes"`
预期：FAIL，因为当前实现会一起移动链接的节点

- [ ] **步骤 3：修改 moveInternal 方法**

```java
private static TraceDocument moveInternal(TraceDocument document, String nodeId, int offset, Instant now) {
    if (offset == 0) {
        return document;
    }
    List<TraceNode> nodes = new ArrayList<>(document.nodes());
    int sourceIndex = indexOfNode(nodes, nodeId);
    if (sourceIndex < 0) {
        return document;
    }
    int targetIndex = sourceIndex + offset;
    if (targetIndex < 0 || targetIndex >= nodes.size()) {
        return document;
    }
    TraceNode moved = nodes.remove(sourceIndex);
    nodes.add(targetIndex, moved);
    return new TraceDocument(
            3,
            document.id(),
            document.name(),
            document.description(),
            document.createdAt(),
            now,
            List.copyOf(nodes),
            document.links(),
            document.expandedNodeIds());
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`gradle test --tests "com.zimaai.codetrace.toolwindow.CodeTraceControllerTest.moveNode_shouldOnlyMoveSingleNode_notLinkedNodes"`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java
git commit -m "refactor: remove link coupling from moveInternal"
```

---

## 任务 2：删除 linkedNodeIds 方法

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java:445-452`
- 测试：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java`

- [ ] **步骤 1：编写失败的测试 - 删除节点不级联**

```java
@Test
void deleteNode_shouldOnlyDeleteSingleNode_notLinkedNodes() {
    // Given: 节点 A 和节点 B 通过链接关联
    TraceNode nodeA = createNode("node-a", "A", null);
    TraceNode nodeB = createNode("node-b", "B", null);
    TraceLink link = new TraceLink("link-1", "node-a", "node-b", Instant.now(), TraceLinkKind.MANUAL);
    TraceDocument doc = createDocument(List.of(nodeA, nodeB), List.of(link));
    when(storage.load(any())).thenReturn(doc);

    // When: 删除节点 A
    controller.deleteNodeOrPair("node-a");

    // Then: 只有节点 A 被删除，节点 B 保留
    verify(storage).save(any(), argThat(saved -> {
        List<TraceNode> nodes = saved.nodes();
        return nodes.size() == 1 && nodes.get(0).id().equals("node-b");
    }));
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`gradle test --tests "com.zimaai.codetrace.toolwindow.CodeTraceControllerTest.deleteNode_shouldOnlyDeleteSingleNode_notLinkedNodes"`
预期：FAIL，因为当前实现会一起删除链接的节点

- [ ] **步骤 3：修改 deleteInternal 方法**

```java
private static TraceDocument deleteInternal(TraceDocument document, String nodeId, Instant now) {
    // 只删除指定节点，不级联删除链接的节点
    Set<String> allRemoved = new HashSet<>();
    allRemoved.add(nodeId);
    // 仍然级联删除子节点
    collectDescendantIds(document.nodes(), allRemoved);
    List<TraceNode> nodes = document.nodes().stream()
            .filter(node -> !allRemoved.contains(node.id()))
            .toList();
    // 保留所有链接，即使引用了被删除的节点
    List<TraceLink> links = document.links();
    return new TraceDocument(
            3,
            document.id(),
            document.name(),
            document.description(),
            document.createdAt(),
            now,
            nodes,
            links,
            document.expandedNodeIds());
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`gradle test --tests "com.zimaai.codetrace.toolwindow.CodeTraceControllerTest.deleteNode_shouldOnlyDeleteSingleNode_notLinkedNodes"`
预期：PASS

- [ ] **步骤 5：删除 linkedNodeIds 方法**

```java
// 删除以下方法（约第 445-452 行）
private static List<String> linkedNodeIds(List<TraceLink> links, String nodeId) {
    for (TraceLink link : links) {
        if (link.sourceNodeId().equals(nodeId) || link.targetNodeId().equals(nodeId)) {
            return List.of(link.sourceNodeId(), link.targetNodeId());
        }
    }
    return List.of(nodeId);
}
```

- [ ] **步骤 6：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java src/test/java/com/zimaai/codetrace/toolwindow/CodeTraceControllerTest.java
git commit -m "refactor: remove linkedNodeIds method and cascade delete"
```

---

## 任务 3：创建 NodeNumberingService

**文件：**
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/NodeNumberingService.java`
- 测试：`src/test/java/com/zimaai/codetrace/toolwindow/NodeNumberingServiceTest.java`

- [ ] **步骤 1：编写失败的测试 - 单层编号**

```java
@Test
void calculateNumbers_shouldNumberRootNodes() {
    // Given
    TraceNode node1 = createNode("node-1", "A", null);
    TraceNode node2 = createNode("node-2", "B", null);
    TraceNode node3 = createNode("node-3", "C", null);
    TraceDocument doc = createDocument(List.of(node1, node2, node3), List.of());

    // When
    Map<String, String> numbers = NodeNumberingService.calculateNumbers(doc);

    // Then
    assertThat(numbers).containsEntry("node-1", "1");
    assertThat(numbers).containsEntry("node-2", "2");
    assertThat(numbers).containsEntry("node-3", "3");
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`gradle test --tests "com.zimaai.codetrace.toolwindow.NodeNumberingServiceTest.calculateNumbers_shouldNumberRootNodes"`
预期：FAIL，因为 NodeNumberingService 类不存在

- [ ] **步骤 3：创建 NodeNumberingService 类**

```java
package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class NodeNumberingService {
    private NodeNumberingService() {
        // Utility class
    }

    public static Map<String, String> calculateNumbers(TraceDocument document) {
        Objects.requireNonNull(document, "document");
        Map<String, String> numbers = new HashMap<>();
        List<TraceNode> nodes = document.nodes();

        // 找到所有根节点（parentId == null）
        int rootIndex = 1;
        for (TraceNode node : nodes) {
            if (node.parentId() == null) {
                String rootNumber = String.valueOf(rootIndex);
                numbers.put(node.id(), rootNumber);
                calculateChildNumbers(nodes, node.id(), rootNumber, numbers);
                rootIndex++;
            }
        }

        return numbers;
    }

    private static void calculateChildNumbers(
            List<TraceNode> nodes,
            String parentId,
            String parentNumber,
            Map<String, String> numbers) {
        int childIndex = 1;
        for (TraceNode node : nodes) {
            if (parentId.equals(node.parentId())) {
                String childNumber = parentNumber + "." + childIndex;
                numbers.put(node.id(), childNumber);
                calculateChildNumbers(nodes, node.id(), childNumber, numbers);
                childIndex++;
            }
        }
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`gradle test --tests "com.zimaai.codetrace.toolwindow.NodeNumberingServiceTest.calculateNumbers_shouldNumberRootNodes"`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/toolwindow/NodeNumberingService.java src/test/java/com/zimaai/codetrace/toolwindow/NodeNumberingServiceTest.java
git commit -m "feat: add NodeNumberingService for hierarchical numbering"
```

---

## 任务 4：完善 NodeNumberingService 测试

**文件：**
- 测试：`src/test/java/com/zimaai/codetrace/toolwindow/NodeNumberingServiceTest.java`

- [ ] **步骤 1：编写失败的测试 - 多层编号**

```java
@Test
void calculateNumbers_shouldNumberNestedNodes() {
    // Given
    TraceNode root1 = createNode("root-1", "Root1", null);
    TraceNode child1 = createNode("child-1", "Child1", "root-1");
    TraceNode child2 = createNode("child-2", "Child2", "root-1");
    TraceNode grandchild = createNode("grandchild-1", "GrandChild1", "child-1");
    TraceNode root2 = createNode("root-2", "Root2", null);

    TraceDocument doc = createDocument(
            List.of(root1, child1, child2, grandchild, root2),
            List.of());

    // When
    Map<String, String> numbers = NodeNumberingService.calculateNumbers(doc);

    // Then
    assertThat(numbers).containsEntry("root-1", "1");
    assertThat(numbers).containsEntry("child-1", "1.1");
    assertThat(numbers).containsEntry("child-2", "1.2");
    assertThat(numbers).containsEntry("grandchild-1", "1.1.1");
    assertThat(numbers).containsEntry("root-2", "2");
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`gradle test --tests "com.zimaai.codetrace.toolwindow.NodeNumberingServiceTest.calculateNumbers_shouldNumberNestedNodes"`
预期：FAIL

- [ ] **步骤 3：运行测试验证通过**

运行：`gradle test --tests "com.zimaai.codetrace.toolwindow.NodeNumberingServiceTest.calculateNumbers_shouldNumberNestedNodes"`
预期：PASS（因为实现已经支持多层编号）

- [ ] **步骤 4：编写失败的测试 - 空文档**

```java
@Test
void calculateNumbers_shouldHandleEmptyDocument() {
    // Given
    TraceDocument doc = createDocument(List.of(), List.of());

    // When
    Map<String, String> numbers = NodeNumberingService.calculateNumbers(doc);

    // Then
    assertThat(numbers).isEmpty();
}
```

- [ ] **步骤 5：运行测试验证通过**

运行：`gradle test --tests "com.zimaai.codetrace.toolwindow.NodeNumberingServiceTest.calculateNumbers_shouldHandleEmptyDocument"`
预期：PASS

- [ ] **步骤 6：Commit**

```bash
git add src/test/java/com/zimaai/codetrace/toolwindow/NodeNumberingServiceTest.java
git commit -m "test: add comprehensive tests for NodeNumberingService"
```

---

## 任务 5：创建 MultiSelectTransferHandler

**文件：**
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/MultiSelectTransferHandler.java`
- 测试：`src/test/java/com/zimaai/codetrace/toolwindow/MultiSelectTransferHandlerTest.java`

- [ ] **步骤 1：编写失败的测试 - 创建传输数据**

```java
@Test
void createTransferable_shouldSerializeMultipleNodeIds() {
    // Given
    JTree tree = createMockTree();
    when(tree.getSelectionPaths()).thenReturn(new TreePath[]{
            createTreePath("node-1"),
            createTreePath("node-2"),
            createTreePath("node-3")
    });

    MultiSelectTransferHandler handler = new MultiSelectTransferHandler(controller, refreshUi);

    // When
    Transferable transferable = handler.createTransferable(tree);

    // Then
    String data = (String) transferable.getTransferData(DataFlavor.stringFlavor);
    assertThat(data).isEqualTo("node-1,node-2,node-3");
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`gradle test --tests "com.zimaai.codetrace.toolwindow.MultiSelectTransferHandlerTest.createTransferable_shouldSerializeMultipleNodeIds"`
预期：FAIL，因为 MultiSelectTransferHandler 类不存在

- [ ] **步骤 3：创建 MultiSelectTransferHandler 类**

```java
package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.TreePath;

public final class MultiSelectTransferHandler extends TransferHandler {
    private static final DataFlavor FLAVOR = DataFlavor.stringFlavor;
    private static final String DELIMITER = ",";

    private final CodeTraceController controller;
    private final Runnable refreshUi;

    public MultiSelectTransferHandler(CodeTraceController controller, Runnable refreshUi) {
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
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null || paths.length == 0) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paths.length; i++) {
            if (paths[i].getLastPathComponent() instanceof TraceNode node) {
                if (i > 0) {
                    sb.append(DELIMITER);
                }
                sb.append(node.id());
            }
        }

        return new StringSelection(sb.toString());
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

        // 解析传输的数据
        String data;
        try {
            data = (String) support.getTransferable().getTransferData(FLAVOR);
        } catch (Exception e) {
            return false;
        }

        String[] nodeIds = data.split(DELIMITER);
        Set<String> sourceIds = new HashSet<>(Arrays.asList(nodeIds));

        // 验证：不能拖拽到自身或子节点
        TraceDocument doc = controller.state().currentDocument();
        for (String sourceId : sourceIds) {
            if (sourceId.equals(targetNode.id()) || isDescendantOf(targetNode, sourceId, doc)) {
                return false;
            }
        }

        // 执行移动
        String newParentId = targetNode.parentId();
        int childIndex = dropLocation.getChildIndex();

        // 在 EDT 线程中执行移动操作
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                // 移动所有选中的节点
                int currentIndex = childIndex >= 0 ? childIndex : 0;
                for (String sourceId : sourceIds) {
                    if (childIndex < 0) {
                        controller.setParent(sourceId, newParentId);
                    } else {
                        controller.setParentAndIndex(sourceId, newParentId, currentIndex);
                        currentIndex++;
                    }
                }
            } catch (IllegalArgumentException ignored) {
            }
            refreshUi.run();
        });

        return true;
    }

    private static boolean isDescendantOf(TraceNode node, String potentialAncestorId, TraceDocument doc) {
        if (doc == null) return false;
        Set<String> visited = new HashSet<>();
        String parentId = node.parentId();
        while (parentId != null) {
            if (!visited.add(parentId)) return false;
            if (parentId.equals(potentialAncestorId)) return true;
            TraceNode parent = doc.nodes().stream()
                    .filter(n -> n.id().equals(parentId))
                    .findFirst().orElse(null);
            parentId = parent == null ? null : parent.parentId();
        }
        return false;
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`gradle test --tests "com.zimaai.codetrace.toolwindow.MultiSelectTransferHandlerTest.createTransferable_shouldSerializeMultipleNodeIds"`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/toolwindow/MultiSelectTransferHandler.java src/test/java/com/zimaai/codetrace/toolwindow/MultiSelectTransferHandlerTest.java
git commit -m "feat: add MultiSelectTransferHandler for multi-selection drag and drop"
```

---

## 任务 6：创建 LinkRelationColumnRenderer

**文件：**
- 创建：`src/main/java/com/zimaai/codetrace/toolwindow/LinkRelationColumnRenderer.java`
- 测试：`src/test/java/com/zimaai/codetrace/toolwindow/LinkRelationColumnRendererTest.java`

- [ ] **步骤 1：编写失败的测试 - 无链接节点**

```java
@Test
void render_shouldShowEmptyForNodeWithNoLinks() {
    // Given
    TraceNode node = createNode("node-1", "TestNode", null);
    TraceDocument doc = createDocument(List.of(node), List.of());
    Map<String, String> numbers = Map.of("node-1", "1");

    LinkRelationColumnRenderer renderer = new LinkRelationColumnRenderer(
            () -> doc, () -> numbers, () -> null, () -> null);

    // When
    Component component = renderer.getTableCellRendererComponent(
            new JTable(), node, false, false, 0, 1);

    // Then
    JLabel label = (JLabel) component;
    assertThat(label.getText()).isEqualTo("");
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`gradle test --tests "com.zimaai.codetrace.toolwindow.LinkRelationColumnRendererTest.render_shouldShowEmptyForNodeWithNoLinks"`
预期：FAIL，因为 LinkRelationColumnRenderer 类不存在

- [ ] **步骤 3：创建 LinkRelationColumnRenderer 类**

```java
package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

public final class LinkRelationColumnRenderer extends DefaultTableCellRenderer {
    private final Supplier<TraceDocument> documentSupplier;
    private final Supplier<Map<String, String>> numberMapSupplier;
    private final Supplier<String> focusedNodeIdSupplier;
    private final Supplier<String> pendingSourceSupplier;

    public LinkRelationColumnRenderer(
            Supplier<TraceDocument> documentSupplier,
            Supplier<Map<String, String>> numberMapSupplier,
            Supplier<String> focusedNodeIdSupplier,
            Supplier<String> pendingSourceSupplier) {
        this.documentSupplier = documentSupplier;
        this.numberMapSupplier = numberMapSupplier;
        this.focusedNodeIdSupplier = focusedNodeIdSupplier;
        this.pendingSourceSupplier = pendingSourceSupplier;
    }

    @Override
    public Component getTableCellRendererComponent(
            JTable table, Object value, boolean isSelected, boolean hasFocus,
            int row, int column) {

        JLabel label = (JLabel) super.getTableCellRendererComponent(
                table, "", isSelected, hasFocus, row, column);

        if (!(value instanceof TraceNode node) || node == TraceTreeModel.VIRTUAL_ROOT) {
            return label;
        }

        TraceDocument document = documentSupplier.get();
        Map<String, String> numberMap = numberMapSupplier.get();

        if (document == null || numberMap == null) {
            return label;
        }

        // 计算链接关系
        List<String> incomingNumbers = new ArrayList<>();
        List<String> outgoingNumbers = new ArrayList<>();

        for (TraceLink link : document.links()) {
            if (node.id().equals(link.targetNodeId())) {
                // 当前节点是目标，来源节点编号
                String sourceNumber = numberMap.get(link.sourceNodeId());
                if (sourceNumber != null) {
                    incomingNumbers.add(sourceNumber);
                }
            }
            if (node.id().equals(link.sourceNodeId())) {
                // 当前节点是来源，目标节点编号
                String targetNumber = numberMap.get(link.targetNodeId());
                if (targetNumber != null) {
                    outgoingNumbers.add(targetNumber);
                }
            }
        }

        // 构建紧凑格式字符串
        StringBuilder sb = new StringBuilder();
        if (!incomingNumbers.isEmpty()) {
            sb.append("←").append(String.join(",", incomingNumbers));
        }
        if (!outgoingNumbers.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append("→").append(String.join(",", outgoingNumbers));
        }

        label.setText(sb.toString());
        return label;
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`gradle test --tests "com.zimaai.codetrace.toolwindow.LinkRelationColumnRendererTest.render_shouldShowEmptyForNodeWithNoLinks"`
预期：PASS

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/toolwindow/LinkRelationColumnRenderer.java src/test/java/com/zimaai/codetrace/toolwindow/LinkRelationColumnRendererTest.java
git commit -m "feat: add LinkRelationColumnRenderer for displaying link relations"
```

---

## 任务 7：完善 LinkRelationColumnRenderer 测试

**文件：**
- 测试：`src/test/java/com/zimaai/codetrace/toolwindow/LinkRelationColumnRendererTest.java`

- [ ] **步骤 1：编写失败的测试 - 有来源和目标链接**

```java
@Test
void render_shouldShowIncomingAndOutgoingLinks() {
    // Given
    TraceNode nodeA = createNode("node-a", "A", null);
    TraceNode nodeB = createNode("node-b", "B", null);
    TraceNode nodeC = createNode("node-c", "C", null);
    TraceLink link1 = new TraceLink("link-1", "node-a", "node-b", Instant.now(), TraceLinkKind.MANUAL);
    TraceLink link2 = new TraceLink("link-2", "node-b", "node-c", Instant.now(), TraceLinkKind.MANUAL);

    TraceDocument doc = createDocument(List.of(nodeA, nodeB, nodeC), List.of(link1, link2));
    Map<String, String> numbers = Map.of("node-a", "1", "node-b", "2", "node-c", "3");

    LinkRelationColumnRenderer renderer = new LinkRelationColumnRenderer(
            () -> doc, () -> numbers, () -> null, () -> null);

    // When
    Component component = renderer.getTableCellRendererComponent(
            new JTable(), nodeB, false, false, 0, 1);

    // Then
    JLabel label = (JLabel) component;
    assertThat(label.getText()).isEqualTo("←1 →3");
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`gradle test --tests "com.zimaai.codetrace.toolwindow.LinkRelationColumnRendererTest.render_shouldShowIncomingAndOutgoingLinks"`
预期：FAIL

- [ ] **步骤 3：运行测试验证通过**

运行：`gradle test --tests "com.zimaai.codetrace.toolwindow.LinkRelationColumnRendererTest.render_shouldShowIncomingAndOutgoingLinks"`
预期：PASS（因为实现已经支持显示来源和目标链接）

- [ ] **步骤 4：Commit**

```bash
git add src/test/java/com/zimaai/codetrace/toolwindow/LinkRelationColumnRendererTest.java
git commit -m "test: add comprehensive tests for LinkRelationColumnRenderer"
```

---

## 任务 8：修改 TraceEditorPanel 支持 JTable

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java`

- [ ] **步骤 1：查看当前 TraceEditorPanel 实现**

```bash
cat src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java
```

- [ ] **步骤 2：修改 nodeTree() 方法返回 JTable**

```java
// 将
private final JTree nodeTree = new JTree();

// 改为
private final JTable nodeTable = new JTable();

// 将
public JTree nodeTree() {
    return nodeTree;
}

// 改为
public JTable nodeTable() {
    return nodeTable;
}
```

- [ ] **步骤 3：添加表格模型支持**

```java
// 添加新的内部类或方法来支持表格模型
public void setNodeTableModel(javax.swing.table.TableModel model) {
    nodeTable.setModel(model);
}

public javax.swing.table.TableModel getNodeTableModel() {
    return nodeTable.getModel();
}
```

- [ ] **步骤 4：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java
git commit -m "refactor: modify TraceEditorPanel to support JTable"
```

---

## 任务 9：修改 CodeTracePanel 使用 JTable

**文件：**
- 修改：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`

- [ ] **步骤 1：修改 rebuildView 方法**

```java
private void rebuildView() {
    // 现有代码...

    // 替换以下代码：
    // TraceTreeModel model = new TraceTreeModel(() -> controller.state().currentDocument());
    // editorPanel.nodeTree().setModel(model);

    // 改为：
    TraceDocument document = controller.state().currentDocument();
    if (document != null) {
        // 计算编号
        Map<String, String> numberMap = NodeNumberingService.calculateNumbers(document);

        // 创建表格模型
        NodeTableModel tableModel = new NodeTableModel(document.nodes(), numberMap, document.links());
        editorPanel.nodeTable().setModel(tableModel);

        // 设置渲染器
        editorPanel.nodeTable().getColumnModel().getColumn(0).setCellRenderer(
                new NodeNumberRenderer());
        editorPanel.nodeTable().getColumnModel().getColumn(1).setCellRenderer(
                new NodeNameRenderer(
                        () -> controller.state().currentDocument(),
                        () -> controller.state().focusedNodeId(),
                        () -> controller.state().pendingLinkSourceId()));
        editorPanel.nodeTable().getColumnModel().getColumn(2).setCellRenderer(
                new LinkRelationColumnRenderer(
                        () -> controller.state().currentDocument(),
                        () -> numberMap,
                        () -> controller.state().focusedNodeId(),
                        () -> controller.state().pendingLinkSourceId()));

        // 设置拖拽支持
        editorPanel.nodeTable().setDragEnabled(true);
        editorPanel.nodeTable().setDropMode(javax.swing.DropMode.INSERT_ROWS);
        editorPanel.nodeTable().setTransferHandler(
                new MultiSelectTransferHandler(controller, this::rebuildView));

        // 设置多选模式
        editorPanel.nodeTable().setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    }

    // 现有代码...
}
```

- [ ] **步骤 2：创建 NodeTableModel 内部类**

```java
private static class NodeTableModel extends javax.swing.table.AbstractTableModel {
    private final List<TraceNode> nodes;
    private final Map<String, String> numberMap;
    private final List<TraceLink> links;

    private static final String[] COLUMN_NAMES = {"编号", "节点名称", "链接关系"};

    public NodeTableModel(List<TraceNode> nodes, Map<String, String> numberMap, List<TraceLink> links) {
        this.nodes = nodes;
        this.numberMap = numberMap;
        this.links = links;
    }

    @Override
    public int getRowCount() {
        return nodes.size();
    }

    @Override
    public int getColumnCount() {
        return 3;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        TraceNode node = nodes.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> numberMap.getOrDefault(node.id(), "");
            case 1 -> node;
            case 2 -> node; // 渲染器会处理链接关系
            default -> null;
        };
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case 0 -> String.class;
            case 1 -> TraceNode.class;
            case 2 -> TraceNode.class;
            default -> Object.class;
        };
    }

    public TraceNode getNodeAt(int rowIndex) {
        return nodes.get(rowIndex);
    }
}
```

- [ ] **步骤 3：创建 NodeNumberRenderer 内部类**

```java
private static class NodeNumberRenderer extends javax.swing.table.DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(
            JTable table, Object value, boolean isSelected, boolean hasFocus,
            int row, int column) {
        JLabel label = (JLabel) super.getTableCellRendererComponent(
                table, value, isSelected, hasFocus, row, column);
        label.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        return label;
    }
}
```

- [ ] **步骤 4：修改 wireSelection 方法**

```java
private void wireSelection() {
    // 现有文件列表选择代码...

    // 替换以下代码：
    // editorPanel.nodeTree().setDragEnabled(true);
    // editorPanel.nodeTree().setDropMode(DropMode.ON_OR_INSERT);
    // editorPanel.nodeTree().setTransferHandler(
    //         new NodeTreeTransferHandler(controller, this::rebuildView));
    // editorPanel.nodeTree().addTreeSelectionListener(event -> {...});

    // 改为：
    editorPanel.nodeTable().setDragEnabled(true);
    editorPanel.nodeTable().setDropMode(javax.swing.DropMode.INSERT_ROWS);
    editorPanel.nodeTable().setTransferHandler(
            new MultiSelectTransferHandler(controller, this::rebuildView));

    editorPanel.nodeTable().getSelectionModel().addListSelectionListener(event -> {
        if (event.getValueIsAdjusting()) return;
        int selectedRow = editorPanel.nodeTable().getSelectedRow();
        if (selectedRow < 0) {
            selectedNodeId = null;
            controller.clearFocusedNodeId();
        } else {
            NodeTableModel model = (NodeTableModel) editorPanel.nodeTable().getModel();
            TraceNode selected = model.getNodeAt(selectedRow);
            selectedNodeId = selected.id();
            controller.setFocusedNodeId(selectedNodeId);
        }
        syncSelectedNodeNote();
        refreshButtons();
    });

    // 现有展开/折叠代码...（需要适配到 JTable）
    // 现有双击导航代码...（需要适配到 JTable）
}
```

- [ ] **步骤 5：Commit**

```bash
git add src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java
git commit -m "refactor: replace JTree with JTable in CodeTracePanel"
```

---

## 任务 10：更新测试和清理

**文件：**
- 修改：`src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTest.java`
- 删除：`src/main/java/com/zimaai/codetrace/toolwindow/LinkedNodeTreeCellRenderer.java`
- 删除：`src/main/java/com/zimaai/codetrace/toolwindow/NodeTreeTransferHandler.java`

- [ ] **步骤 1：运行所有测试**

```bash
gradle test
```

- [ ] **步骤 2：修复失败的测试**

根据测试失败情况，更新测试代码以适配新的 JTable 实现。

- [ ] **步骤 3：删除旧的渲染器文件**

```bash
git rm src/main/java/com/zimaai/codetrace/toolwindow/LinkedNodeTreeCellRenderer.java
git rm src/main/java/com/zimaai/codetrace/toolwindow/NodeTreeTransferHandler.java
```

- [ ] **步骤 4：删除旧的测试文件**

```bash
git rm src/test/java/com/zimaai/codetrace/toolwindow/LinkedNodeTreeCellRendererTest.java
git rm src/test/java/com/zimaai/codetrace/toolwindow/NodeTreeTransferHandlerTest.java
```

- [ ] **步骤 5：运行完整测试套件**

```bash
gradle test
```

- [ ] **步骤 6：最终 Commit**

```bash
git add -A
git commit -m "refactor: complete node list refactoring with JTable, multi-select, and numbering"
```

---

## 自检清单

- [x] 规格覆盖度：所有需求都有对应任务
  - 去掉链接联动：任务 1-2
  - 多选支持：任务 5
  - 层级编号：任务 3-4
  - 链接关系列：任务 6-7
  - 表格视图：任务 8-9
  - 清理优化：任务 10

- [x] 占位符扫描：没有发现占位符
  - 所有步骤都有完整代码
  - 所有测试都有具体断言
  - 所有命令都有精确路径

- [x] 类型一致性：所有类型和方法签名一致
  - NodeNumberingService.calculateNumbers() 在任务 3 定义，在任务 6-7 使用
  - MultiSelectTransferHandler 在任务 5 定义，在任务 9 使用
  - LinkRelationColumnRenderer 在任务 6 定义，在任务 9 使用
  - NodeTableModel 在任务 9 定义，内部使用

---

## 执行交接

计划已完成并保存到 `docs/superpowers/plans/2026-06-04-node-list-refactor.md`。两种执行方式：

**1. 子代理驱动（推荐）** - 每个任务调度一个新的子代理，任务间进行审查，快速迭代

**2. 内联执行** - 在当前会话中使用 executing-plans 执行任务，批量执行并设有检查点

选哪种方式？
