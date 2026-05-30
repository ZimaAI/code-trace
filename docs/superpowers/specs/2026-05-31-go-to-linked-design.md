# Go To Linked — 合并导航按钮设计

## 目标

将 TraceEditorPanel 工具栏中的 "Go to Source" 和 "Go to Target" 两个按钮合并为一个 "Go To Linked" 按钮。当选中节点有且仅有一个关联节点时，点击直接跳转；当有多个关联节点时，弹出分组菜单供选择。

## 数据层变更

### CodeTracePanel.java

**新增数据结构**：

```java
private record LinkedNodes(
    List<TraceNode> sources,  // 从其他节点 → 选中节点（选中节点为 Target）
    List<TraceNode> targets   // 从选中节点 → 其他节点（选中节点为 Source）
) {}
```

- `sources`：选中节点作为 Target 的链接中，对应的 Source 节点列表
- `targets`：选中节点作为 Source 的链接中，对应的 Target 节点列表

**替换方法**：

| 旧方法 | 新方法 |
|---|---|
| `findSelectedLink()` (返回单个 TraceLink) | `findAllLinksForSelectedNode()` (返回 List<TraceLink>) |
| `findLinkedSourceNode()` | 合并到 `findAllLinkedNodes()` |
| `findLinkedTargetNode()` | 合并到 `findAllLinkedNodes()` |
| `goToLinkedSource()` | `goToLinked()` |
| `goToLinkedTarget()` | `goToLinked()` |

**新增方法**：

- `findAllLinksForSelectedNode()` — 遍历所有 links，返回 selectedNodeId 为 sourceNodeId 或 targetNodeId 的全部 link
- `findAllLinkedNodes()` — 调用上面的方法，按方向分类组装为 `LinkedNodes`
- `hasLinkedNodes()` — 是否至少有一个关联节点
- `goToLinked()` — 根据关联节点总数决定行为：
  - 0 个：不做任何事（按钮应该 disabled）
  - 1 个：直接调用 `controller.navigateToNode()`
  - 多个：构建 JPopupMenu 并显示在按钮正下方

**refreshButtons() 变更**：

```java
// 旧
editorPanel.goToSourceButton().setEnabled(linkedSourceNode != null);
editorPanel.goToTargetButton().setEnabled(linkedTargetNode != null);

// 新
editorPanel.goToLinkedButton().setEnabled(hasLinkedNodes());
```

## UI 层变更

### TraceEditorPanel.java

**按钮替换**：

- 移除字段：`goToSourceButton`、`goToTargetButton`
- 新增字段：`goToLinkedButton`，文字 "Go To Linked"，图标 `AllIcons.Actions.Find`
- 初始状态 `setEnabled(false)`

**工具栏布局**：

- 原位置（`nodeToolbar` 最后一组）的两个按钮替换为一

**Getter 替换**：

- 移除：`goToSourceButton()`、`goToTargetButton()`
- 新增：`goToLinkedButton()`

### 弹出菜单设计

**结构**：

```
┌─────────────────────────┐
│ Sources (→)             │  ← 粗体禁用项，灰色文字
│   B (displayName)       │  ← 可点击 JMenuItem
│   C                      │
│ ───────────────────────  │  ← JSeparator（仅两侧都有数据时）
│ Targets (←)             │
│   D                      │
└─────────────────────────┘
```

**菜单构建逻辑**：

1. 如果 `sources` 列表非空，添加 "Sources (→)" 标签项（setEnabled(false)，加粗）
2. 遍历 sources，每个添加一个 JMenuItem，action 为 `controller.navigateToNode(node)`
3. 如果 sources 和 targets 都非空，添加 JSeparator
4. 如果 targets 列表非空，添加 "Targets (←)" 标签项
5. 遍历 targets，每个添加一个 JMenuItem

**触发位置**：`goToLinkedButton.getBounds()` 确定位置，`popupMenu.show(goToLinkedButton, 0, button.getHeight())`

**菜单关闭**：点击菜单项后自动关闭（JPopupMenu 默认行为）

## 测试影响

### CodeTracePanelNavigationTest.java

- 原来测试 `goToSourceButton` 和 `goToTargetButton` 独立 enable/disable 的场景需要更新为测试 `goToLinkedButton`
- 新增测试用例：选中节点有 2 个 sources 时弹出菜单的验证
- 新增测试用例：选中节点既有 sources 又有 targets 时菜单分组正确

### CodeTracePanelTest.java

- `findButton("Go to Source")` / `findButton("Go to Target")` 替换为 `findButton("Go To Linked")`

## 边界情况

1. **选中节点无链接**：按钮 disabled
2. **选中节点仅有一个方向（只有 source 或只有 target）**：弹出菜单仅显示有数据的分组，不显示空分组、不显示分隔线
3. **文档为空或未选中节点**：按钮 disabled
4. **关联节点中有同名的**：仍然分别列出，不合并（用户需要完整信息区分）
5. **外部 action 触发的导航**：`goToLinked()` 仅在用户点击按钮/菜单时调用，不影响 `navigateToNode` 的其他调用点
