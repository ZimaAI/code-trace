# Code Trace Reading 生成规则设计

## 概述

这个设计聚焦调整 `code-trace-reading` skill 的 trace 生成规则，解决三个已确认问题：

- 新建 trace 文件仍沿用 `trace-<unix-seconds>.json`，文件名不反映本次阅读主题
- skill 没有把“调用语句节点”与“被调声明节点”写成明确的成对记录规则
- link 约束不够明确，容易生成不是节点对、或者一个 source 对多个 target 的链路

目标是在不改动 trace JSON 基础 schema 的前提下，统一新建文件命名规则，收紧关键调用的节点记录方式，并把 link 的语义限定为稳定的一对一 source-to-target 关系。

## 目标

- 新建 trace 文件默认命名为 `yyyyMMdd-主题.json`。
- 文件名中的 `主题` 允许中文，只做最小文件名清洗。
- 对最终保留在主链中的关键跨符号调用，强制记录 source 节点和 target 节点。
- source 节点使用调用方中的单行完整代码。
- target 节点使用被调方法或函数的声明行。
- source 与 target 之间必须建立直接 link，表达 `source -> target`。
- 生成结果中的每个 source 最多只能对应一个 target。
- 更新已有 trace 时，只对本次新增或本次改动涉及的链路套用新规则。

## 非目标

- 不修改当前 trace JSON 的顶层字段结构，不引入 `edges`、`pairs` 或其他新字段。
- 不要求把所有普通调用都展开为节点对，只约束最终保留在主链里的关键调用。
- 不批量重命名现有 `code-trace/` 目录下的历史 JSON 文件。
- 不在辅助脚本中引入跨语言 AST 分析，自动判断某个节点是否一定是调用语句或声明行。
- 不对未触及的旧 trace 做一次性整体格式治理。

## 已确认的产品规则

- 新建 trace 文件命名采用 `yyyyMMdd-主题.json`。
- 文件名中的 `主题` 允许中文，不强制做英文 slug。
- 文件名只做最小清洗，移除或替换非法文件名字符，并处理首尾空白。
- 如果清洗后主题为空，回退为可写的默认主题名。
- 同一天、同主题重名时，不覆盖已有文件，应追加去重后缀。
- 只对最终纳入主链的关键调用强制成对记录 source 与 target。
- source 节点取调用方中的单行完整代码。
- target 节点取被调方法或函数的声明行。
- 每个 source 最多只能对应一个 target。
- 同一个 target 可以被多个 source 指向。
- 更新已有 trace 时，只修正本次新增或改动涉及到的链路，不整体重整旧文件。

## 当前实现分析

当前 `code-trace-reading` skill 及其参考材料存在以下缺口：

- [SKILL.md](/D:/Develop/Projects/code-trace/.agents/skills/code-trace-reading/SKILL.md) 仍建议新建文件名采用 `trace-<unix-seconds>.json`
- [trace-schema.md](/D:/Develop/Projects/code-trace/.agents/skills/code-trace-reading/references/trace-schema.md) 只定义了 `nodes` 和 `links` 的结构，没有约束关键调用必须落成 source/target 节点对
- [trace-tools.js](/D:/Develop/Projects/code-trace/.agents/skills/code-trace-reading/scripts/trace-tools.js) 当前只校验 schema、节点存在性、基础 link 合法性，不校验 source 对多个 target 的语义冲突

这意味着即使操作者理解正确，当前仓库也没有把这些规则沉淀为可复用规范；而一旦操作者理解偏差，也没有轻量校验可以及时发现错误。

## 设计方案

### 方案总览

采用“规范 + 校验”的最小充分方案：

- 更新 skill 文案，明确新建文件命名规则和关键调用成对记录规则
- 更新 schema 参考文档，补充 link 的语义约束和推荐示例
- 更新辅助脚本，只对可稳定判断的约束做校验

这个方案覆盖本次需求，同时避免把 `trace-tools.js` 扩张成一套不可靠的语言分析器。

### 规则模型

#### 新建文件命名

- 新建 trace 默认文件名改为 `yyyyMMdd-主题.json`
- `yyyyMMdd` 使用当前创建日期
- `主题` 来自本次 trace 的主题标题
- 允许中文，不要求转成英文 slug
- 只移除或替换文件系统非法字符，如 `\/:*?"<>|`
- 清理首尾空白和明显无意义的分隔符
- 如果主题清洗后为空，回退到默认主题名，例如 `未命名主题`
- 如果目标文件名已存在，自动追加去重后缀，例如 `yyyyMMdd-主题-2.json`

#### 关键调用成对记录

只对最终保留在主链中的关键跨符号调用强制成对落点。

一旦某次关键调用被纳入主链，就必须同时存在两个节点：

- source 节点：调用方中的单行完整代码，例如 `TraceDocument document = storage.load(fileName);`
- target 节点：被调方法或函数的声明行，例如 `public TraceDocument load(String fileName) {`

这两个节点之间必须存在一条直接 link，方向固定为 `source -> target`。

#### Link 语义收紧

生成结果中的 link 只允许表示节点对之间的明确关系。

规则如下：

- 一个 source 最多只能对应一个 target
- 同一个 target 可以被多个 source 指向
- 不允许自链接
- 不允许悬空链接
- 不允许为了补全图而建立与当前关键调用无关的 link

这次不要求把旧文件的所有历史 link 立即清洗干净，只要求新建 trace 和本次改动涉及的链路符合该语义。

## 产物与职责分工

### Skill 文案

[SKILL.md](/D:/Develop/Projects/code-trace/.agents/skills/code-trace-reading/SKILL.md) 负责定义操作者应如何生成和更新 trace。

需要补充的内容包括：

- 将默认文件名从 `trace-<unix-seconds>.json` 调整为 `yyyyMMdd-主题.json`
- 明确 `主题` 允许中文，仅做最小清洗
- 将“关键调用成对记录”写为显式流程规则
- 明确 source 节点必须是单行完整调用代码
- 明确 target 节点必须是被调方法或函数声明行
- 明确每个 source 最多只能对应一个 target
- 明确更新已有 trace 时，只修正本次新增或改动涉及的链路

### Schema 参考文档

[trace-schema.md](/D:/Develop/Projects/code-trace/.agents/skills/code-trace-reading/references/trace-schema.md) 继续作为 JSON 字段说明文档，但补充语义级规则：

- 新建文件命名规范
- source/target 节点对的建模语义
- link 的唯一性约束
- 关键调用建链的推荐示例

这份文档不新增字段，只定义现有字段该如何被使用。

### 辅助脚本

[trace-tools.js](/D:/Develop/Projects/code-trace/.agents/skills/code-trace-reading/scripts/trace-tools.js) 负责轻量辅助，不负责深度代码理解。

本次应支持的方向包括：

- `new` 命令支持按 `yyyyMMdd-主题.json` 生成默认输出文件名
- `validate` 命令继续保留基础 schema 校验
- `validate` 增加 source 对多个 target 的冲突检查
- `validate` 继续检查自环、悬空引用、重复 id 等结构错误

本次不在脚本里尝试自动判定某个 `displayName` 是否真的是调用语句或声明行，因为这类判断跨语言且不稳定。

## 兼容性与错误处理

### 历史文件兼容

- 旧文件继续可读
- 历史文件名如果仍是 `trace-*.json`，不因为命名不符合新规则而直接判错
- 旧 trace 中未触及的历史 link 暂不整体治理

这样可以保证现有仓库内容不需要一次性迁移。

### 更新旧文件的处理边界

- 如果本次是在已有 trace 上补充节点或链路，则仅要求本次新增或改动到的链路遵守新规则
- 如果旧文件里已存在一个 source 指向多个 target，但这次没有修改到该链路，则先保持原样
- 如果这次正好修改到该历史冲突链路，则按新规则收敛为每个 source 只保留一个 target

### 文件名异常处理

- 主题包含非法文件名字符时，先做最小清洗
- 清洗后为空时，回退到默认主题名
- 如果目标文件已存在，则追加递增后缀，避免覆盖

### 校验失败反馈

`validate` 应给出可定位的错误信息，至少覆盖以下情况：

- 某个 source 节点指向多个 target
- link 自环
- link 引用了不存在的 node
- node id 或 link id 重复

对于“节点内容是否足够像调用语句或声明行”这类无法稳定自动判断的问题，不做硬错误，只保留为文档规则。

## 测试与验证策略

### 文档一致性

- skill 文案、schema 参考、脚本帮助信息需要使用同一套命名和链路术语
- 示例中的 source/target 节点含义必须与规则描述一致

### 脚本验证

至少覆盖以下场景：

- 新建 trace 时，默认文件名符合 `yyyyMMdd-主题.json`
- 中文主题可以生成合法文件名
- 非法字符会被清洗
- 同名文件会生成去重后缀
- 一个 source 对多个 target 时，`validate` 报错
- target 被多个 source 指向时，`validate` 允许通过
- 自环或悬空 link 时，`validate` 报错

### 手工检查

手工补充检查以下行为：

- 按新规则新建的 trace 文件能够继续被 IDEA 插件正常打开
- 在已有 trace 上只增补局部链路时，不会强制要求整体重构历史内容

## 风险与约束

### 风险一：文件名规则与历史习惯冲突

已有使用者可能习惯 `trace-<unix-seconds>.json`，切换后短期内会同时存在两种文件名风格。

控制措施：

- 对历史文件保持兼容
- 在 skill 和 schema 文档中明确“新建采用新规则，旧文件不强制迁移”

### 风险二：过度依赖脚本判断节点语义

如果在 `validate` 中强行判定某个节点是不是“真正的调用语句”或“真正的声明行”，会因为语言差异和片段上下文不足导致误报。

控制措施：

- 脚本只校验结构性与可机械判断的语义约束
- 节点语义质量继续由 skill 规则约束

## 验收标准

- 新建 trace 的默认文件名采用 `yyyyMMdd-主题.json`
- 主题允许中文，且会进行最小文件名清洗
- 主链中保留的关键调用会同时生成 source 节点和 target 节点
- source 节点内容为调用方单行完整代码
- target 节点内容为被调方法或函数声明行
- source 与 target 之间存在直接 link，方向为 `source -> target`
- 生成结果中每个 source 最多只能对应一个 target
- 同一个 target 可以被多个 source 指向
- 更新已有 trace 时，只要求本次新增或改动涉及的链路符合新规则
- 辅助脚本可以发现基础结构错误和 source 对多个 target 的冲突
