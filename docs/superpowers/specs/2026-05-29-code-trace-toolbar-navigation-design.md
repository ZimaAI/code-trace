# Code Trace 工具栏精简与链接跳转设计

## 概述

这个设计聚焦优化 `code-trace` Tool Window 当前的按钮布局与链接导航体验，解决三个已确认问题：

- 窗口顶部全局工具栏存在重复按钮，和右侧编辑区动作重复，增加了噪音
- 已建立链接的节点之间缺少显式双向跳转入口，只能双击跳到当前节点
- 工具窗口变窄后，节点列表上方的按钮可能被隐藏，不利于稳定操作

目标是在不引入无关重构的前提下，精简全局工具栏、补上 source/target 跳转能力，并让节点区按钮在窄宽度下始终可见。

## 目标

- 顶部全局工具栏只保留 `Refresh`。
- 节点区工具条保留现有编辑和链接动作，并新增 `Go to Source` 与 `Go to Target`。
- 当节点存在链接关系时，用户可以显式跳转到该 link 的 source 节点代码或 target 节点代码。
- 窗口缩窄时，节点区按钮允许自动换行，不能被隐藏或折叠。
- 继续复用现有节点导航机制，不为这次优化新建第二套代码跳转链路。

## 非目标

- 不改动左侧文件列表的动作结构。
- 不修改双击节点跳转当前节点的既有行为。
- 不新增右键菜单、浮层、图形化关系视图或其他链接可视化能力。
- 不重构 `CodeTraceController` 与 `CodeNavigationService` 的整体分层。
- 不处理工具窗口以外的编辑器动作或外部命令入口。

## 已确认的产品规则

- 顶部全局工具栏只保留 `Refresh`。
- 节点区按钮顺序固定为：
  `Edit / Delete / Move Up / Move Down / Set as Source / Link To Here / Unlink / Go to Source / Go to Target`
- `Go to Source / Go to Target` 始终显示，不满足条件时置灰。
- 选中未链接节点时，两个跳转按钮都置灰。
- 选中已链接节点时：
  - `Go to Source` 总是跳到该 link 的 source 节点
  - `Go to Target` 总是跳到该 link 的 target 节点
- 这意味着选中 source 节点时可以跳到自己或 target；选中 target 节点时可以跳到 source 或自己。
- 如果 link 还在但另一端节点缺失，相关跳转按钮置灰，不新增提示弹窗。
- 工具窗口缩窄时，节点区按钮必须通过换行保持可见，不能隐藏。

## 当前实现分析

当前实现中，动作分散在两个位置：

- `CodeTracePanel` 顶部创建全局工具栏，包含 `Refresh / Save Trace Note / Save Node Note / Set as Source / Link To Here / Unlink`
- `TraceEditorPanel` 的节点区工具条包含 `Edit / Delete / Move Up / Move Down / Set as Source / Link To Here / Unlink`

这带来两个问题：

1. 顶部按钮与节点区按钮职责重复，尤其是节点相关动作重复出现。
2. 顶部和节点区的布局职责不清，导致窄宽度下真正需要持续可见的节点动作没有明确的布局保障。

另外，当前节点导航只支持两种形式：

- 双击节点后调用 `controller.navigateToNode(selectedNode)`
- 由 `CodeNavigationService.navigate(TraceNode)` 根据节点的 `filePath` 与 `line` 打开代码

现有实现已经具备单节点导航能力，但没有基于 `TraceDocument.links()` 解析 source/target 对端节点并导航的入口。

## 设计方案

### 方案总览

采用最小改动方案：

- 顶部全局工具栏只保留 `Refresh`
- trace note 的保存继续留在 trace note 区块底部
- 节点相关动作全部集中到节点区工具条
- 在节点区工具条新增两个固定显示的跳转按钮
- 复用现有 `controller.navigateToNode(...)` 和 `CodeNavigationService`
- 让节点区工具条改用支持自动换行的普通 Swing 容器布局

这个方案直接覆盖当前需求，不引入新的导航服务，也不扩大到无关的 UI 重组。

### 界面结构调整

#### 顶部全局工具栏

`CodeTracePanel` 顶部工具栏只保留：

- `Refresh`

以下按钮从顶部移除：

- `Save Trace Note`
- `Save Node Note`
- `Set as Source`
- `Link To Here`
- `Unlink`

其中：

- `Save Trace Note` 继续保留在 trace note 区块底部
- `Save Node Note` 继续保留在 node note 区块底部
- `Set as Source / Link To Here / Unlink` 继续保留在节点区工具条

这样顶部工具栏只承担“刷新当前文件”的全局职责，不再承担节点编辑职责。

#### 节点区工具条

节点列表上方的工具条调整为：

- `Edit`
- `Delete`
- `Move Up`
- `Move Down`
- `Set as Source`
- `Link To Here`
- `Unlink`
- `Go to Source`
- `Go to Target`

按钮顺序保持固定，不根据状态动态重排。

### 窄窗口布局行为

当前节点区工具条需要从可能产生溢出隐藏的布局，调整为支持自动换行的普通 Swing 容器。

规则如下：

- 按钮从左到右排列
- 超出当前宽度后自动换到下一行
- 不隐藏、不折叠到更多菜单、不改成滚动条
- `link status` 继续保留在节点区底部，不参与按钮换行

因为顶部只剩 `Refresh`，顶部区域不需要额外的换行或溢出处理。

## 链接跳转设计

### 跳转入口

新增两个节点区按钮：

- `Go to Source`
- `Go to Target`

这两个按钮始终可见，仅通过启用状态反映当前是否可操作。

### 跳转解析规则

基于当前选中节点，UI 读取 `currentDocument.links()` 并查找包含该节点的唯一 link。

规则如下：

1. 如果当前没有选中节点，两个按钮都置灰。
2. 如果选中节点不属于任何 link，两个按钮都置灰。
3. 如果选中节点属于某条 link：
   - `Go to Source` 指向该 link 的 `sourceNodeId`
   - `Go to Target` 指向该 link 的 `targetNodeId`
4. 如果目标 node id 找不到对应 `TraceNode`，对应按钮置灰。

由于当前产品规则限制“每个节点最多参与一条 link”，这里不需要处理多 link 分支选择。

### 导航链路复用

“解析 linked source/target node”的逻辑放在 `CodeTracePanel`，实现为小范围 UI 辅助逻辑：

- 根据当前选中节点找到关联 `TraceLink`
- 根据 `sourceNodeId / targetNodeId` 找到对应的 `TraceNode`
- 最终调用现有 `controller.navigateToNode(node)`

真正的代码跳转仍复用现有导航链路：

- `CodeTracePanel`
- `CodeTraceController.navigateToNode(...)`
- `CodeNavigationService.navigate(TraceNode)`

这保证双击节点跳转和按钮跳转共用同一条底层导航机制。

### 状态刷新规则

`Go to Source / Go to Target` 的启用状态与其他节点按钮一样，由 `refreshButtons()` 统一管理。

状态重算时机包括：

- 节点列表选中变化
- `link` 后视图重建
- `unlink` 后视图重建
- `Refresh` 后文档重载
- 删除 linked pair 或普通节点后选中状态变化

## 类与职责调整

### CodeTracePanel

主改动集中在 `CodeTracePanel`：

- 顶部工具栏只创建 `Refresh`
- 增加 `Go to Source / Go to Target` 两个节点动作
- 在 `refreshButtons()` 中统一管理新按钮状态
- 增加辅助方法解析当前选中节点关联的 `TraceLink`
- 增加辅助方法根据 link 两端 ID 查找 `TraceNode`
- 新增“跳到 source/target”按钮事件处理

`CodeTracePanel` 继续承担 UI 级动作编排职责，不把这次轻量链接解析强行下沉到更高层。

### TraceEditorPanel

`TraceEditorPanel` 负责节点区工具条的结构调整：

- 增加 `Go to Source` 与 `Go to Target` 按钮成员与访问器
- 将节点区工具条容器调整为可自动换行的布局
- 保持 `Save Trace Note`、`Save Node Note` 和 `link status` 的现有职责边界

### CodeTraceController

`CodeTraceController` 不新增复杂导航接口，继续只暴露：

- `navigateToNode(TraceNode node)`

这样 controller 仍然只负责单节点导航，避免为这次需求引入“根据当前节点推导 link 对端”的额外业务接口。

### CodeNavigationService

`CodeNavigationService` 不需要行为调整，继续负责：

- 根据 `TraceNode.filePath`
- 解析项目内或绝对路径
- 用 `line` 打开目标文件位置

## 错误处理

- 当前无选中节点时，两个跳转按钮置灰。
- 当前节点未链接时，两个跳转按钮置灰。
- link 存在但 source 或 target 节点缺失时，只对缺失侧按钮置灰。
- 点击可用跳转按钮后，如果底层 `navigateToNode(...)` 返回失败，保持现有静默失败行为，不新增提示弹窗。
- 双击当前节点的现有跳转行为不因新增按钮而改变。

## 测试策略

### CodeTracePanel 测试

覆盖以下场景：

- 顶部全局工具栏只剩 `Refresh`
- 顶部不再暴露重复的 `Save Node Note / Set as Source / Link To Here / Unlink`
- 节点区存在 `Go to Source / Go to Target`
- 未链接节点下两个跳转按钮都置灰
- 选中 linked source 节点时，两个跳转按钮都可用，分别指向 source 与 target
- 选中 linked target 节点时，两个跳转按钮都可用，分别指向 source 与 target
- `link / unlink / refresh / delete linked pair` 后按钮状态随当前选中节点正确刷新

### 导航行为测试

覆盖以下场景：

- 双击节点仍然跳到当前节点
- `Go to Source / Go to Target` 最终复用 `navigateToNode(...)`
- 如果 link 存在但对端节点缺失，相关按钮不可用

### 布局回归验证

对窄窗口布局不写依赖具体像素位置的脆弱断言，而采用以下验证方式：

- 组件级检查：节点区工具条使用支持自动换行的容器布局
- 手工 smoke check：缩窄 Tool Window 后，所有节点区按钮仍可见，只是换到下一行

## 风险与约束

### 风险一：Swing 布局换行行为

普通 `JPanel` 默认 `FlowLayout` 虽然支持换行，但在 IntelliJ Tool Window 中的具体表现依赖父容器尺寸传播。

控制措施：

- 选用明确支持换行的布局方案
- 保持按钮容器层级简单，避免嵌套滚动或额外 split 影响首选尺寸
- 用手工 smoke check 验证真实窄宽度行为

### 风险二：按钮状态与链接状态不同步

新增两个跳转按钮后，如果状态刷新没有统一走 `refreshButtons()`，容易出现按钮可点但目标不存在的 UI 错乱。

控制措施：

- 所有新按钮状态统一放入 `refreshButtons()`
- 任何改变选中项或链接关系的动作都通过既有 `rebuildView()` 或选中监听触发状态刷新

## 验收标准

- 顶部全局工具栏只显示 `Refresh`。
- trace note 与 node note 保存按钮仍分别位于各自编辑区域，不再在顶部重复出现。
- 节点区工具条包含 `Go to Source / Go to Target`，并保持确认过的按钮顺序。
- 选中未链接节点时，两个跳转按钮不可用。
- 选中已链接节点时，用户可以显式跳到该 link 的 source 节点代码或 target 节点代码。
- 双击节点跳转当前节点的现有行为保持不变。
- 窗口缩窄时，节点区按钮不会隐藏，而是自动换到下一行。
