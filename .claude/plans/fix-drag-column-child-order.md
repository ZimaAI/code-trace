# 修复拖拽、列宽度和子节点位置问题

## 问题根因分析

### 问题 1：节点不能拖动改变位置
**根因：** `MultiSelectTransferHandler` 中的 `createTransferable()` 和 `importData()` 方法将表格模型强制转换为 `NodeTableModel`，但在引入折叠/展开功能后，模型被替换为 `FilteredNodeTableModel`，导致 `ClassCastException`。

### 问题 2：列宽度重置
**根因：** `rebuildView()` 方法每次调用都会重置列宽度为 1:5:1 比例，没有保存/恢复用户调整的列宽度机制。

### 问题 3：子节点位置错误
**根因：** `moveNode()`、`setParent()`、`setParentAndIndex()` 等方法只移动单个节点，不移动其子节点，破坏了父子顺序不变量（子节点必须在父节点下方）。

---

## 修复方案

### 修复 1：MultiSelectTransferHandler 类型转换

**文件：** `src/main/java/com/zimaai/codetrace/toolwindow/MultiSelectTransferHandler.java`

**修改：**
1. 将 `createTransferable()` 和 `importData()` 中的 `(NodeTableModel) table.getModel()` 改为获取 `FilteredNodeTableModel`
2. 更新 `getSiblingIndex()` 方法签名，接受 `FilteredNodeTableModel`

**代码变更：**
```java
// 第 44 行：createTransferable()
FilteredNodeTableModel model = (FilteredNodeTableModel) table.getModel();

// 第 80 行：importData()
FilteredNodeTableModel model = (FilteredNodeTableModel) table.getModel();

// 第 155 行：getSiblingIndex()
private static int getSiblingIndex(FilteredNodeTableModel model, TraceNode targetNode) {
```

---

### 修复 2：列宽度保存/恢复

**文件：** `src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`

**修改：**
1. 添加字段保存用户调整的列宽度
2. 在 `rebuildView()` 中，只在首次加载或文档切换时设置默认宽度
3. 用户调整列宽度时保存当前宽度

**代码变更：**
```java
// 添加字段
private int[] savedColumnWidths = null;
private boolean columnWidthsInitialized = false;

// 在 rebuildView() 中修改列宽度设置逻辑
// 只在首次加载时设置默认宽度
if (!columnWidthsInitialized) {
    int totalWidth = editorPanel.nodeTable().getWidth();
    if (totalWidth <= 0) {
        totalWidth = 420;
    }
    int unit = totalWidth / 7;
    editorPanel.nodeTable().getColumnModel().getColumn(0).setPreferredWidth(unit);
    editorPanel.nodeTable().getColumnModel().getColumn(1).setPreferredWidth(unit * 5);
    editorPanel.nodeTable().getColumnModel().getColumn(2).setPreferredWidth(unit);
    columnWidthsInitialized = true;
}

// 添加列宽度监听器保存用户调整
editorPanel.nodeTable().getColumnModel().addColumnModelListener(new javax.swing.event.ColumnModelListener() {
    @Override
    public void columnMarginChanged(javax.swing.event.ChangeEvent e) {
        saveColumnWidths();
    }
    // 其他方法空实现
});

private void saveColumnWidths() {
    if (editorPanel.nodeTable().getColumnModel().getColumnCount() >= 3) {
        savedColumnWidths = new int[] {
            editorPanel.nodeTable().getColumnModel().getColumn(0).getWidth(),
            editorPanel.nodeTable().getColumnModel().getColumn(1).getWidth(),
            editorPanel.nodeTable().getColumnModel().getColumn(2).getWidth()
        };
    }
}

private void restoreColumnWidths() {
    if (savedColumnWidths != null && editorPanel.nodeTable().getColumnModel().getColumnCount() >= 3) {
        editorPanel.nodeTable().getColumnModel().getColumn(0).setPreferredWidth(savedColumnWidths[0]);
        editorPanel.nodeTable().getColumnModel().getColumn(1).setPreferredWidth(savedColumnWidths[1]);
        editorPanel.nodeTable().getColumnModel().getColumn(2).setPreferredWidth(savedColumnWidths[2]);
    }
}
```

---

### 修复 3：移动节点时移动子节点

**文件：** `src/main/java/com/zimaai/codetrace/toolwindow/TraceDocumentEditor.java`

**修改：**
1. 添加 `collectDescendantIds()` 方法收集所有子孙节点 ID
2. 修改 `setParentAndIndex()` 方法，移动节点时同时移动其所有子孙节点
3. 修改 `moveNode()` 相关方法，移动节点时同时移动其所有子孙节点

**代码变更：**
```java
// 添加方法：收集所有子孙节点 ID
private static List<String> collectDescendantIds(List<TraceNode> nodes, String parentId) {
    List<String> descendants = new ArrayList<>();
    for (TraceNode node : nodes) {
        if (parentId.equals(node.parentId())) {
            descendants.add(node.id());
            descendants.addAll(collectDescendantIds(nodes, node.id()));
        }
    }
    return descendants;
}

// 修改 setParentAndIndex() 方法
public TraceDocument setParentAndIndex(TraceDocument document, String nodeId, String newParentId, int targetIndex, Instant now) {
    // ... 现有验证代码 ...
    
    List<TraceNode> nodes = new ArrayList<>(document.nodes());
    int sourceIndex = indexOfNode(nodes, nodeId);
    TraceNode source = nodes.get(sourceIndex);
    
    // 收集所有子孙节点
    List<String> descendantIds = collectDescendantIds(nodes, nodeId);
    
    // 移除源节点及其所有子孙节点
    List<TraceNode> subtree = new ArrayList<>();
    subtree.add(source);
    for (String descId : descendantIds) {
        int descIndex = indexOfNode(nodes, descId);
        if (descIndex >= 0) {
            subtree.add(nodes.get(descIndex));
        }
    }
    nodes.removeAll(subtree);
    
    // 计算插入位置
    int insertIndex;
    if (newParentId == null) {
        // 成为根节点
        if (targetIndex < 0 || targetIndex >= countRootNodes(nodes)) {
            insertIndex = countRootNodes(nodes);
        } else {
            insertIndex = findRootNodeIndex(nodes, targetIndex);
        }
    } else {
        // 成为子节点
        List<String> siblings = collectChildrenIds(nodes, newParentId);
        if (targetIndex < 0 || targetIndex >= siblings.size()) {
            insertIndex = findLastChildIndex(nodes, newParentId) + 1;
        } else {
            insertIndex = indexOfNode(nodes, siblings.get(targetIndex));
        }
    }
    
    // 揉入子树
    nodes.addAll(insertIndex, subtree);
    
    return new TraceDocument(
        document.schemaVersion(),
        document.id(),
        document.name(),
        document.description(),
        document.createdAt(),
        now,
        nodes,
        document.links(),
        document.expandedNodeIds()
    );
}
```

---

## 测试计划

### 测试 1：拖拽功能
- 创建包含父子节点的文档
- 验证拖拽节点不抛出 ClassCastException
- 验证拖拽后节点位置正确

### 测试 2：列宽度保持
- 调整列宽度
- 执行任意操作触发 rebuildView()
- 验证列宽度保持不变

### 测试 3：子节点位置
- 创建父子节点结构
- 移动父节点
- 验证子节点跟随移动，保持在父节点下方

---

## 实现顺序

1. **修复 1：MultiSelectTransferHandler 类型转换**（最简单，解决拖拽功能）
2. **修复 2：列宽度保存/恢复**（中等难度，解决列宽度问题）
3. **修复 3：移动节点时移动子节点**（最复杂，解决子节点位置问题）
