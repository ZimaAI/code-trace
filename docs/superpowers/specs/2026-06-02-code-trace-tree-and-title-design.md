# code-trace 树形结构与标题增强设计

> 将节点的扁平列表升级为多层级树形结构，支持展开/折叠、拖拽设子节点、聚焦定位图标和 title 字段。

**状态：** 已批准
**日期：** 2026-06-02

---

## 1. 数据模型

### TraceNode 新增字段

```java
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
    String parentId,   // 新增：父节点 ID（null 表示根节点）
    String title       // 新增：别名/标签（null 或空表示无 title）
) {}
```

### TraceDocument 变更

```java
public record TraceDocument(
    int schemaVersion,              // 从 2 升到 3
    String id,
    String name,
    String description,
    Instant createdAt,
    Instant updatedAt,
    List<TraceNode> nodes,
    List<TraceLink> links,
    Set<String> expandedNodeIds    // 新增：持久化展开状态
) {}
```

### 向后兼容

`TraceJsonMapper` 在读到 `schemaVersion < 3` 的旧文档时：
- 所有节点的 `parentId` 设为 `null`
- `title` 设为 `null`
- `expandedNodeIds` 设为空集合

---

## 2. TreeModel 与视图

### 组件替换

`TraceEditorPanel` 中的 `JBList<TraceNode>` 替换为 `javax.swing.JTree`。

### Custom TreeModel

新建 `TraceTreeModel` 实现 `javax.swing.tree.TreeModel`：

- `getRoot()` — 返回虚拟根节点（不显示），其 children 是所有 `parentId == null` 的节点
- `getChild(parent, index)` — 从 nodes 列表中筛选 `parentId == parent.id` 的子节点，按列表顺序返回
- `getChildCount(parent)` — 返回子节点数量
- `isLeaf(node)` — 无子节点即为叶子
- 每次 `rebuildView()` 时重新构建 TreeModel 并设置到 JTree

### Cell Renderer

新建 `LinkedNodeTreeCellRenderer` 实现 `DefaultTreeCellRenderer`：

- **展开按钮**：JTree 原生提供，有子节点时自动显示在节点左侧
- **定位图标**：当 `node.id == state.focusedNodeId()` 时，在最左侧显示 `AllIcons.General.Locate`
- **Title 显示**：`title` 有值时显示 `"title — displayName"`，无值时直接显示 `displayName`。title 过长时截断并用 `…` 表示（最大宽度约 `JBUI.scale(120)` px）
- **角色图标**：保持现有 `▶`（source）、`◀`（target）、`◉`（pending source）前缀
- **背景色**：保持现有 source/target/pending source 着色逻辑
- **Title 颜色**：title 部分使用 `JBColor.GRAY` 与 displayName 形成视觉层次

---

## 3. 展开/折叠与树操作

### 展开状态持久化

- 监听 `JTree` 的 `treeWillExpand` / `treeWillCollapse` 事件
- 在 `CodeTracePanel` 中维护 `expandedNodeIds` 集合
- 每次 `persist()` 时将展开状态写入 `TraceDocument.expandedNodeIds`
- 加载文档后遍历 `expandedNodeIds` 逐个调用 `tree.expandPath(...)` 恢复

### 控制器层新增/修改

| 方法 | 说明 |
|------|------|
| `setParent(nodeId, parentId)` | 将节点移动到目标父节点下（移出时 parentId=null） |
| `addChildNode(parentId, node)` | 在指定父节点末尾插入子节点 |
| `insertNodeAt(node, parentId, index)` | 在指定父节点下指定位置插入 |
| `moveNodeOrPair(nodeId, offset)` | 改为在同级内移动 |
| `moveNodeOrPairToIndex(nodeId, targetIndex)` | 改为在同级内移动到目标索引 |
| `deleteNodeOrPair(nodeId)` | 级联删除所有子孙节点 |
| `setExpandedNodes(Set<String>)` | 持久化展开状态 |

`TraceDocumentEditor` 新增对应的纯文档操作。

---

## 4. 拖拽交互

### 新建 NodeTreeTransferHandler

替换 `NodeListReorderTransferHandler`，支持两种拖拽模式：

- **正常拖拽（同层级排序）**：鼠标 x 坐标与目标节点缩进级别差值 < 20px → 在目标节点所在层级调整顺序
- **缩进拖拽（设为子节点）**：鼠标 x 坐标比目标节点缩进级别多 20px 以上 → 设为目标节点的子节点
- **移出拖拽**：将子节点拖到更浅的缩进级别 → 将 `parentId` 设为 `null`（或上级节点的同级）

### 视觉反馈

- 使用 `JTree.setDropLocation(dropLocation)` 绘制插入线
- 检测到缩进模式时，插入线向右偏移

### 链接节点成组移动

保持现有逻辑：被拖拽节点有 link 时，link 的另一端节点一起移动（作为兄弟节点插入到同一父节点下）。

---

## 5. Title 编辑

### 编辑入口

在现有 `showNodeDialog` 对话框中新增 `Title` 字段（排在 Display Name 上方）。

### 渲染截断

- title 使用 `SwingUtilities.computeStringWidth` 计算像素宽度
- 超出 `JBUI.scale(120)` 的部分截断，追加 `…`
- title 颜色使用 `JBColor.GRAY`

---

## 6. 测试策略

### 新增/修改测试

| 测试类 | 变更 |
|--------|------|
| `CodeTraceControllerTest` | 新增 setParent、级联删除、同级移动测试 |
| `CodeTracePanelTreeTest`（新建） | 展开/折叠、拖拽缩进、聚焦图标、title 显示 |
| `LinkedNodeTreeCellRendererTest`（新建） | title 截断、聚焦图标显示 |
| `TraceJsonMapperTest` | 向后兼容测试（schema v2 → v3 迁移） |
| 其他现有测试 | 适配 TreeModel 替代 JList 的 API 变更 |

---

## 7. 文件变更清单

### 修改
- `src/main/java/com/zimaai/codetrace/model/TraceNode.java`
- `src/main/java/com/zimaai/codetrace/model/TraceDocument.java`
- `src/main/java/com/zimaai/codetrace/storage/TraceJsonMapper.java`
- `src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java`
- `src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java`
- `src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
- `src/main/java/com/zimaai/codetrace/toolwindow/TraceDocumentEditor.java`
- `src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java`
- `src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceHandler.java`

### 新建
- `src/main/java/com/zimaai/codetrace/toolwindow/TraceTreeModel.java`
- `src/main/java/com/zimaai/codetrace/toolwindow/LinkedNodeTreeCellRenderer.java`
- `src/main/java/com/zimaai/codetrace/toolwindow/NodeTreeTransferHandler.java`

### 删除
- `src/main/java/com/zimaai/codetrace/toolwindow/LinkedNodeListCellRenderer.java`
- `src/main/java/com/zimaai/codetrace/toolwindow/NodeListReorderTransferHandler.java`

### 测试修改/新增
- 修改所有涉及 `JBList` / `nodeList()` 的测试
- 新建 `CodeTracePanelTreeTest.java`
- 新建 `LinkedNodeTreeCellRendererTest.java`
- 修改 `TraceJsonMapperTest.java`
