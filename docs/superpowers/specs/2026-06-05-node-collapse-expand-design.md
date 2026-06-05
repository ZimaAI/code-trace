# 节点展开/折叠功能设计文档

## 概述

在现有的 JTable 扁平列表中实现节点的展开/折叠功能，支持父子节点层级显示、连接线、状态持久化。

## 设计决策

### 1. 折叠按钮渲染

**位置**：编号列（列 0）的左侧

**实现**：
- 创建 `CollapseIndicatorRenderer` 扩展 `DefaultTableCellRenderer`
- 判断节点是否有子节点（通过 `parentId` 关联）
- 有子节点时显示 IntelliJ 的 `AllIcons.General.ArrowDown` / `AllIcons.General.ArrowRight`
- 无子节点时不显示图标，保持原有缩进

**视觉效果**：
```
▼ 1    RootNode1
    ├── 1.1  ChildNode1
    ├── 1.2  ChildNode2
▶ 2    RootNode2
▼ 3    RootNode3
    └── 3.1  ChildNode3
```

**点击处理**：
- 在 JTable 上添加 `MouseListener`
- 点击编号列时，判断点击位置是否在图标区域
- 触发折叠/展开逻辑

### 2. 过滤模型（FilteredNodeTableModel）

**类结构**：
```java
public class FilteredNodeTableModel extends AbstractTableModel {
    private final NodeTableModel sourceModel;
    private final TraceDocument document;
    private List<TraceNode> visibleNodes;
    private Map<String, Integer> nodeIdToVisibleIndex;
}
```

**核心逻辑**：
- `rebuildVisibleNodes()` - 根据 `expandedNodeIds` 重建可见节点列表
- 遍历源模型的节点，跳过被折叠父节点的子节点
- 维护 `nodeIdToVisibleIndex` 映射，用于快速查找

**可见性判断**：
```java
boolean isVisible(TraceNode node) {
    // 向上遍历父节点链
    // 如果任意祖先节点被折叠（不在 expandedNodeIds 中），返回 false
}
```

**事件转发**：
- 监听 `NodeTableModel` 的 `TableModelEvent`
- 转发给 JTable，但重新映射行索引

### 3. 连接线显示

**渲染位置**：编号列（列 0）

**连接线字符**：
- `├──` - 中间子节点
- `└──` - 最后一个子节点
- `│` - 父节点的延续线
- （空格）- 无延续线

**渲染逻辑**：
- 遍历节点的祖先链，判断每一层是否是该层的最后一个子节点
- 根据判断结果组合连接线字符串

**示例**：
```
▼ 1    RootNode1
│ ├── 1.1  ChildNode1
│ └── 1.2  ChildNode2
▶ 2    RootNode2
▼ 3    RootNode3
  └── 3.1  ChildNode3
```

### 4. 折叠/展开交互逻辑

**触发方式**：
- 点击编号列的折叠图标
- 双击整行（可选快捷方式）

**折叠操作流程**：
1. 将节点 ID 从 `expandedNodeIds` 移除
2. 调用 `FilteredNodeTableModel.rebuildVisibleNodes()`
3. 通知 JTable 刷新
4. 保存 `TraceDocument` 到文件

**展开操作流程**：
1. 将节点 ID 添加到 `expandedNodeIds`
2. 调用 `FilteredNodeTableModel.rebuildVisibleNodes()`
3. 通知 JTable 刷新
4. 保存 `TraceDocument` 到文件

**批量操作**：
- 支持"全部展开"和"全部折叠"功能
- 可添加到右键菜单或工具栏

**状态同步**：
- 拖拽操作后，如果父节点被折叠，自动展开父节点
- 删除父节点时，子节点提升为根节点

### 5. 编号重新计算

**编号规则**：
- 根节点：`1`, `2`, `3`, ...
- 子节点：`父节点编号.子节点索引`
  - 如 `1.1`, `1.2`, `1.3`
  - 如 `1.1.1`, `1.1.2`（多层嵌套）

**实现方式**：
- `NodeNumberingService` 已存在，但当前可能基于完整节点列表计算
- 需要修改为基于 `visibleNodes` 计算编号
- 或者在 `FilteredNodeTableModel` 中重新实现编号逻辑

**折叠时的编号**：
- 折叠父节点后，子节点隐藏，编号不显示
- 展开后，子节点编号恢复

**示例**：
```
▼ 1    RootNode1          (折叠前)
│ ├── 1.1  ChildNode1
│ └── 1.2  ChildNode2
▶ 2    RootNode2

▼ 1    RootNode1          (折叠后，子节点隐藏)
▶ 2    RootNode2
```

### 6. 状态持久化

**存储字段**：`TraceDocument.expandedNodeIds`（已有）

**持久化时机**：
- 折叠/展开操作后立即保存
- 批量操作（全部展开/折叠）后保存

**加载逻辑**：
- 打开文档时，从 `expandedNodeIds` 读取状态
- 如果 `expandedNodeIds` 为空，默认全部展开
- 应用状态到 `FilteredNodeTableModel`

**兼容性处理**：
- 旧文档可能没有 `expandedNodeIds` 字段
- 默认值为空 `Set`，表示全部展开
- 保存时自动添加该字段

**序列化**：
- 使用现有的 JSON 序列化机制
- `expandedNodeIds` 序列化为字符串数组

## 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      JTable (UI)                            │
│  ┌─────────────┬─────────────────┬─────────────────────┐   │
│  │  编号列      │  节点名称列      │  链接关系列          │   │
│  │  (渲染器)    │                 │                     │   │
│  └─────────────┴─────────────────┴─────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              FilteredNodeTableModel                         │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  visibleNodes: List<TraceNode>                      │   │
│  │  nodeIdToVisibleIndex: Map<String, Integer>         │   │
│  │  rebuildVisibleNodes()                              │   │
│  │  isVisible(node): boolean                           │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                NodeTableModel (源模型)                       │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  nodes: List<TraceNode>                             │   │
│  │  getNodeAt(index): TraceNode                        │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              TraceDocument (数据源)                          │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  nodes: List<TraceNode>                             │   │
│  │  expandedNodeIds: Set<String>                       │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

## 文件变更清单

### 新增文件
1. `FilteredNodeTableModel.java` - 过滤模型
2. `CollapseIndicatorRenderer.java` - 折叠按钮渲染器

### 修改文件
1. `NodeTableModel.java` - 添加获取子节点的方法
2. `TraceEditorPanel.java` - 集成过滤模型和渲染器
3. `CodeTraceController.java` - 处理折叠/展开事件
4. `TraceDocumentEditor.java` - 状态持久化

## 测试场景

1. **基本折叠/展开**
   - 点击父节点的折叠按钮
   - 验证子节点隐藏/显示
   - 验证连接线正确显示

2. **多层嵌套**
   - 折叠顶层父节点
   - 验证所有子孙节点隐藏
   - 展开后验证层级恢复

3. **状态持久化**
   - 折叠节点后关闭文档
   - 重新打开验证状态保持

4. **边界情况**
   - 空文档
   - 只有根节点
   - 所有节点都是子节点
