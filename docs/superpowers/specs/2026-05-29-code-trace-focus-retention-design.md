# Code Trace 节点选中保持设计

## 概述

这个设计聚焦修复 `code-trace` 插件在节点操作后的交互退化问题：

- 对节点执行保存、移动、链接、刷新等操作后，当前节点选中状态会被重置
- 从编辑器执行 `Add to code-trace` 后，新加入的节点不会自动成为当前选中项

目标是在不扩大改动范围的前提下，稳定节点列表的选中行为，让用户在连续编辑 trace 时不需要重复找回上下文。

## 目标

- 普通节点操作后保持原节点仍为当前选中项。
- 从外部动作新增节点后，自动切换并选中新加入或复用的 `source` 节点。
- 删除当前选中节点后清空选中，不自动跳转到相邻节点。
- 避免列表刷新期间的临时空选中事件污染真实选中状态。
- 保持现有 Swing 面板结构和 controller 职责边界，不为这次修复引入无关重构。

## 非目标

- 不处理键盘焦点落点，例如是否把键盘焦点还给列表或文本框。
- 不修改文件列表、trace note 输入框或其他非节点列表组件的焦点行为。
- 不引入新的图形化视图、复杂状态容器或全局 UI 状态管理方案。
- 不调整 `Add to code-trace` 的节点捕获、目标识别或导航逻辑本身。

## 已确认的产品规则

- 保存 trace note、保存 node note、设置 source、手动 link、unlink、编辑节点、移动节点、刷新当前文件后，节点列表保持原选中项。
- `Add to code-trace` 后，自动切换并选中本次的 `source` 节点。
- 如果 `Add to code-trace` 复用了已有节点，也要切过去选中这个已有节点。
- 如果一次外部动作同时新增了 `source` 和 `target` 并建立 detected link，最终仍然优先选中 `source`。
- 删除当前选中节点，或删除当前选中节点所在的 linked pair 后，清空节点选中状态。

## 当前问题分析

当前实现中，`CodeTracePanel` 自己维护 `selectedNodeId`，并在 `rebuildView()` 中通过以下流程重建节点列表：

1. `editorPanel.nodeList().setListData(...)`
2. `restoreSelection(...)`

问题在于 `setListData(...)` 会触发 `JList` 选择变化事件。现有 `ListSelectionListener` 会在这一时刻读取空选中值，并立刻把 `selectedNodeId` 改成 `null`。结果是：

- 刷新前真实的选中节点 ID 丢失
- `restoreSelection(...)` 无法恢复原节点
- 逻辑退回到默认选择第一个节点

另外，外部入口 `AddToCodeTraceHandler` 在新增或复用节点后只会触发 `refreshUi.run()`，但不会把“这次刷新后应该选中哪个节点”的意图传递回 `CodeTracePanel`，因此无法满足新增后自动选中新节点的交互预期。

## 设计方案

### 方案总览

采用“面板本地选中状态 + 一次性选中意图”的最小改动方案：

- `CodeTracePanel` 继续持有稳定的当前选中项 `selectedNodeId`
- 给节点列表刷新过程增加同步保护，屏蔽刷新期间的临时空选中事件
- 在 `CodeTraceState` 中增加一次性的 `preferredSelectedNodeId`
- 外部新增动作把目标选中节点 ID 写入这个一次性状态
- `CodeTracePanel.rebuildView()` 在重建节点列表时优先消费一次性选中意图

这个方案直接覆盖当前问题，不需要把长期选中状态整体上移到 controller/state，也不需要为所有 controller 方法设计新的返回类型。

### 选中状态模型

#### 稳定选中状态

`CodeTracePanel` 保留现有的 `selectedNodeId` 作为节点列表的稳定选中状态，负责表达“用户当前正在编辑或操作哪个节点”。

这个状态只在以下场景被修改：

- 用户主动切换节点列表选中项
- 明确要求清空选中，例如删除当前节点
- 刷新完成后根据一次性选中意图或恢复逻辑得到的新选中项

#### 列表同步保护

在 `CodeTracePanel` 增加节点列表同步保护标记，只包裹节点列表刷新和恢复选中的过程。

当保护标记开启时：

- 节点列表的 `ListSelectionListener` 不写入 `selectedNodeId`
- 不同步 node note
- 不触发依赖当前节点的按钮状态重算

这样可以避免 `setListData(...)` 造成的瞬时空选中事件把真实状态覆盖掉。

#### 一次性选中意图

在 `CodeTraceState` 中新增 `preferredSelectedNodeId`，语义为“下一次 UI 重建时优先选中的节点 ID”。

规则：

- 只服务下一次 `rebuildView()`
- 消费完成后立即清空
- 不是长期 UI 状态，不参与普通用户点击选中逻辑

这个状态只用于跨边界传递刷新后的目标选中项，尤其是从编辑器外部动作回流到 Tool Window 的场景。

### 选中恢复优先级

`CodeTracePanel.restoreSelection(...)` 调整为以下优先级：

1. 如果存在 `preferredSelectedNodeId`，优先尝试选中它。
2. 否则如果当前 `selectedNodeId` 仍然存在于刷新后的文档中，恢复它。
3. 如果两者都找不到，清空选中。

明确取消当前“默认选中第一个节点”的兜底策略，因为这会制造错误的上下文切换，与用户确认的删除后清空选中规则冲突。

## 操作行为设计

### 普通操作

以下操作都遵循“保留原选中项”的规则：

- `Save Trace Note`
- `Save Node Note`
- `Set as Source`
- `Link To Here`
- `Unlink`
- `Edit Node`
- `Move Up`
- `Move Down`
- `Refresh`

这些操作不设置 `preferredSelectedNodeId`。它们只依赖已有的 `selectedNodeId` 和新的恢复逻辑来保持原节点不变。

### 删除操作

删除当前选中节点前，UI 显式清空本地 `selectedNodeId`，然后再执行 controller 删除和视图重建。

结果：

- 如果删除的是单个节点，刷新后无选中
- 如果删除的是 linked pair 中的一个节点，整对删除后同样无选中

这个规则避免系统擅自跳到相邻节点，保持删除后的状态明确可预期。

### 外部新增节点

`AddToCodeTraceHandler` 在执行 `addOrReuseNode(source)` 之后，解析出当前文档中的真实 `sourceId`，并将其写入 `preferredSelectedNodeId`。

后续行为：

- 若没有 detected target，刷新后选中 `source`
- 若用户确认 detected target 并新增或复用 target，再创建 link，刷新后仍然选中 `source`
- 若 `source` 本身就是复用的已有节点，刷新后选中这个已存在节点

也就是说，外部动作的最终选中规则始终围绕 `source`，不受 `target` 是否新增或是否成功链接影响。

## 类与职责调整

### CodeTracePanel

主改动放在 `CodeTracePanel`：

- 增加节点列表同步保护标记
- 调整节点列表 `ListSelectionListener`，让它在同步保护期间忽略刷新产生的伪事件
- 调整 `rebuildView()`，在刷新节点列表时消费 `preferredSelectedNodeId`
- 调整 `restoreSelection(...)`，移除默认选第一个节点的逻辑
- 删除节点前显式清空本地选中状态

`CodeTracePanel` 继续负责 UI 级别的选中维护，不把它强行上移到更高层。

### CodeTraceState

新增一次性选中意图字段和对应的设置、清除接口：

- 设置下一次优先选中的节点 ID
- 读取当前一次性选中意图
- 在 UI 消费后清除

`load(...)` 也需要清空这个字段，避免文件切换时把旧意图带到新文档。

### CodeTraceController

controller 只提供薄封装，向上暴露“设置下一次优先选中节点”的明确入口，减少 action 或 panel 直接操纵 state 细节的耦合。

这保持现有分层风格，同时让这次选中修复的写入点可测试、可读。

### AddToCodeTraceHandler

在确定 `sourceIndex` 后，从当前文档解析真实的 `sourceId`，通过 controller 写入一次性选中意图，再继续 detected target 流程，最后刷新 UI。

这个 handler 不负责直接操作 Swing 组件，它只负责把新增操作的选中目标传给 controller/state。

## 错误处理

- 如果 `preferredSelectedNodeId` 在刷新后的文档中不存在，UI 直接清空选中，不回退到第一个节点。
- 如果 `Add to code-trace` 的 capture 失败、link 校验失败或未选中文件，保持现有错误提示行为，不额外污染当前选中状态。
- 如果 detected target 新增或链接失败，但 source 已经成功写入，仍应在刷新后选中 source。
- 如果文件切换导致当前文档变化，旧文档上的 `preferredSelectedNodeId` 必须被丢弃。

## 测试策略

### CodeTracePanel 行为测试

覆盖以下场景：

- 普通刷新后保持原节点选中
- 保存 note 后保持原节点选中
- move 后保持原节点选中
- link / unlink 后保持原节点选中
- 删除当前节点后清空选中
- 恢复目标不存在时不再默认选中第一个节点

重点验证刷新期间不会因为 `setListData(...)` 触发的临时空选中而丢失原始 `selectedNodeId`。

### AddToCodeTraceHandler 测试

覆盖以下场景：

- 新增 source 后，记录的优先选中节点为 source
- source 复用已有节点时，记录的优先选中节点仍为该已有节点
- 新增并链接 detected target 后，最终记录的优先选中节点仍为 source
- capture 失败时不写入优先选中意图

### Controller / State 测试

覆盖以下场景：

- 一次性选中意图可以被设置、读取、清除
- 文件切换或重新加载时，一次性选中意图被重置

## 风险与约束

### 风险一：Swing 事件时序

节点列表刷新和选择恢复都依赖 Swing 事件顺序。若同步保护范围定义不清，仍可能出现状态错乱。

控制措施：

- 把保护范围限定在节点列表数据替换与恢复选中的最小闭包内
- 用行为测试覆盖刷新前后选中值变化

### 风险二：状态分散

长期选中状态在 panel，一次性选中意图在 state，这本身是分层折中。

控制措施：

- 明确两者职责不同：一个表达当前 UI 状态，一个表达下一次刷新意图
- 不把 `preferredSelectedNodeId` 扩展成长期状态，避免后续语义混乱

## 验收标准

- 对节点执行保存、移动、链接、unlink、刷新后，节点列表仍保持原选中项。
- `Add to code-trace` 后，新加或复用的 source 节点自动成为当前选中项。
- 即使同一次外部动作还新增或复用了 target，并建立 detected link，最终选中的仍是 source。
- 删除当前选中节点或其 linked pair 后，节点列表没有任何选中项。
- 节点列表刷新后，不再因为恢复失败而自动跳到第一个节点。
- 选中保持逻辑只影响节点列表，不改变其他组件的焦点策略。
