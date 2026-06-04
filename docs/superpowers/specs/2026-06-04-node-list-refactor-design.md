# 节点列表重构设计规格说明

**日期**: 2026-06-04
**状态**: 已批准
**范围**: 重构节点列表的显示和交互方式

## 1. 概述

本次重构旨在改进节点列表的用户体验，主要包括：
- 去掉链接节点之间的联动行为
- 支持多选节点批量操作
- 添加层级编号系统
- 用表格视图替代树视图，显示链接关系

## 2. 需求详情

### 2.1 去掉链接联动

**当前行为**: 移动或删除节点时，会一起处理链接的节点对
**目标行为**: 链接仅用于跳转，不影响移动和删除操作

**修改范围**:
- `CodeTraceController.moveInternal()`: 删除 `linkedNodeIds` 调用
- `CodeTraceController.moveInternalToIndex()`: 删除 `linkedNodeIds` 调用
- `CodeTraceController.deleteInternal()`: 删除 `linkedNodeIds` 调用
- 删除 `CodeTraceController.linkedNodeIds()` 方法

### 2.2 多选支持

**交互方式**:
- Ctrl+点击: 非连续多选
- Shift+点击: 连续范围选择

**拖拽行为**:
- 拖到节点上方: 所有选中节点变成目标节点的子节点
- 拖到节点之间: 所有选中节点移动到该位置，保持原有顺序

**实现要点**:
- 修改 `JTree` 的选择模型为 `TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION`
- 创建 `MultiSelectTransferHandler` 替换 `NodeTreeTransferHandler`
- 传输数据格式: 逗号分隔的节点 ID 列表

### 2.3 层级编号系统

**编号规则**:
- 根节点 (parentId == null): 1, 2, 3, ...
- 子节点: 父节点编号 + "." + 子节点序号
- 示例:
  - 根节点 1 → 子节点 1.1, 1.2, 1.3
  - 根节点 2 → 子节点 2.1, 2.2

**实现要点**:
- 创建 `NodeNumberingService` 类
- 方法 `calculateNumbers(TraceDocument document)` 返回 `Map<String, String>`
- 编号基于节点在文档中的顺序 (`document.nodes()` 的顺序)
- 递归处理所有层级

### 2.4 链接关系列

**表格结构**:
- 第一列: 编号 (来自 NodeNumberingService)
- 第二列: 节点名称 (保留现有焦点指示器、标题显示)
- 第三列: 链接关系

**链接关系格式**:
- 紧凑格式: `←1,3 →2,5`
- 含义: 节点 1 和 3 链接到当前节点，当前节点链接到节点 2 和 5

**实现要点**:
- 使用 `JTable` 替换 `JTree`
- 创建 `LinkRelationColumnRenderer`
- 链接关系计算: 遍历 `document.links()` 找到当前节点的所有链接，将节点 ID 转换为编号
- 保留双击导航功能
- 适配拖拽功能到 JTable

## 3. 架构设计

### 3.1 新增类

1. **NodeNumberingService**
   - 职责: 计算节点编号
   - 方法: `calculateNumbers(TraceDocument): Map<String, String>`

2. **MultiSelectTransferHandler**
   - 职责: 处理多选拖拽
   - 继承: `TransferHandler`
   - 方法: 重写 `createTransferable`, `importData`

3. **LinkRelationColumnRenderer**
   - 职责: 渲染表格单元格
   - 实现: `TableCellRenderer`
   - 方法: `getTableCellRendererComponent`

### 3.2 修改类

1. **CodeTraceController**
   - 删除链接联动逻辑
   - 保留链接跳转功能

2. **CodeTracePanel**
   - 替换 `JTree` 为 `JTable`
   - 集成新的渲染器和 TransferHandler
   - 适配多选逻辑

3. **TraceEditorPanel**
   - 修改节点列表组件为 JTable
   - 适配现有按钮和事件处理

## 4. 数据流

### 4.1 编号计算流程

```
TraceDocument
    ↓
NodeNumberingService.calculateNumbers()
    ↓
Map<String, String> (nodeId → number)
    ↓
LinkRelationColumnRenderer 使用
```

### 4.2 链接关系计算流程

```
TraceDocument.links()
    ↓
遍历找到当前节点的所有链接
    ↓
将链接的节点 ID 通过编号 Map 转换为编号
    ↓
拼接为紧凑格式字符串
    ↓
LinkRelationColumnRenderer 显示
```

### 4.3 多选拖拽流程

```
用户选择多个节点 (Ctrl/Shift + 点击)
    ↓
MultiSelectTransferHandler.createTransferable()
    ↓
传输逗号分隔的节点 ID 列表
    ↓
importData() 解析目标位置
    ↓
调用 controller 批量移动节点
    ↓
刷新 UI
```

## 5. 错误处理

### 5.1 多选拖拽验证

- 拖拽到自身或子节点: 忽略操作
- 目标位置无效: 忽略操作
- 部分节点移动失败: 记录日志，继续处理其他节点

### 5.2 编号计算异常

- 文档为空: 返回空 Map
- 节点循环引用: 抛出异常或记录警告

### 5.3 链接关系显示

- 链接的节点已被删除: 忽略该链接
- 编号 Map 中找不到节点 ID: 显示 "??"

## 6. 测试策略

### 6.1 单元测试

1. **NodeNumberingServiceTest**
   - 测试单层节点编号
   - 测试多层节点编号
   - 测试空文档
   - 测试节点顺序变化

2. **MultiSelectTransferHandlerTest**
   - 测试 Ctrl 多选
   - 测试 Shift 范围选择
   - 测试拖拽到节点上方
   - 测试拖拽到节点之间
   - 测试无效拖拽操作

3. **LinkRelationColumnRendererTest**
   - 测试无链接节点
   - 测试有来源链接
   - 测试有目标链接
   - 测试双向链接
   - 测试链接节点已删除

### 6.2 集成测试

1. **CodeTracePanelTest**
   - 测试表格显示
   - 测试多选交互
   - 测试拖拽功能
   - 测试双击导航

2. **CodeTraceControllerTest**
   - 测试去掉联动后的移动操作
   - 测试去掉联动后的删除操作
   - 测试链接跳转功能保留

### 6.3 手动测试

- 多选各种场景 (Ctrl, Shift, 混合)
- 拖拽到不同位置
- 编号显示正确性
- 链接关系显示正确性
- 性能测试 (大量节点)

## 7. 迁移计划

### 7.1 阶段一: 去掉链接联动

- 修改 `CodeTraceController`
- 更新相关测试
- 验证现有功能不受影响

### 7.2 阶段二: 实现编号系统

- 创建 `NodeNumberingService`
- 编写单元测试
- 集成到现有模型

### 7.3 阶段三: 实现多选支持

- 创建 `MultiSelectTransferHandler`
- 修改 `JTree` 选择模型
- 编写单元测试
- 集成到 `CodeTracePanel`

### 7.4 阶段四: 实现表格视图

- 创建 `LinkRelationColumnRenderer`
- 替换 `JTree` 为 `JTable`
- 适配现有功能 (选择、拖拽、导航)
- 编写集成测试

### 7.5 阶段五: 清理和优化

- 删除旧的 `LinkedNodeTreeCellRenderer`
- 删除旧的 `NodeTreeTransferHandler`
- 性能优化
- 文档更新

## 8. 风险和缓解措施

### 8.1 风险: JTable 拖拽复杂度

**缓解措施**:
- 参考现有 `NodeTreeTransferHandler` 实现
- 分阶段实现，先实现基本拖拽，再优化交互

### 8.2 风险: 多选状态管理

**缓解措施**:
- 使用 JTable 内置的选择模型
- 清晰定义选择状态的生命周期

### 8.3 风险: 性能问题

**缓解措施**:
- 编号计算结果缓存
- 链接关系按需计算
- 大量节点时考虑分页或虚拟滚动

### 8.4 风险: 现有功能回归

**缓解措施**:
- 完整的单元测试覆盖
- 集成测试验证关键路径
- 分阶段迁移，每阶段验证

## 9. 成功标准

- [ ] 链接节点移动/删除时不再联动
- [ ] 支持 Ctrl+点击和 Shift+点击多选
- [ ] 多选节点可以一起移动，保持顺序
- [ ] 节点左侧显示正确的层级编号
- [ ] 表格第三列正确显示链接关系 (←1,3 →2,5)
- [ ] 双击导航功能正常工作
- [ ] 拖拽功能正常工作
- [ ] 所有现有测试通过
- [ ] 新功能有完整的测试覆盖
