# code-trace 项目理解指南

## 如何使用这份指南

这份文档不是功能说明书，而是面向开发者的读码路线图。目标是帮助你尽快回答 3 个问题：

1. 这个项目想解决什么问题。
2. IntelliJ 插件是如何启动、响应动作并更新 UI 的。
3. `code-trace/*.json` 这套数据结构如何被采集、编辑、持久化和再次导航回代码。

建议先按本文的阅读顺序通读一遍，再按章节逐步展开。后续如果需要，我可以继续把每一章补成独立的详细教程文件。

## 项目理解目标

阅读完这套指南后，你应该能建立这些认识：

- 这是一个 IntelliJ Platform 插件，而不是普通命令行工具或 Web 服务。
- 插件的两个用户入口分别是编辑器右键动作和左侧 `code-trace` Tool Window。
- 项目核心职责分成 `actions`、`toolwindow`、`storage`、`navigation`、`model` 五层。
- trace 数据以项目根目录下的 `code-trace/*.json` 文件持久化，运行态状态由 `CodeTraceState` 管理。
- 主要业务闭环是：采集代码节点 -> 写入 trace 文档 -> 在 Tool Window 中编辑/连线 -> 双击节点导航回源码。

## 推荐阅读顺序

1. 项目概览与运行环境
2. 插件入口与启动流程
3. 顶层目录与模块分工
4. 编辑器动作到 trace 写入的主流程
5. Tool Window UI 与控制层
6. trace 数据模型与 JSON 持久化
7. 导航机制与路径解析
8. 测试结构与回归重点
9. 配置、构建与发布边界

前 1 到 3 章解决「项目是什么」。前 4 到 7 章解决「代码怎么跑起来」。第 8 到 9 章解决「如何安全改动」。

## 章节目录

### 1. 项目概览与运行环境

- 为什么读：
  先确认项目类型、运行宿主和依赖边界，否则后续很容易用普通 Java 应用的思路误判插件生命周期。
- 重点阅读：
  `build.gradle.kts`、`settings.gradle.kts`、`src/main/resources/META-INF/plugin.xml`
- 你将理解：
  项目使用 Java 21、Gradle、`org.jetbrains.intellij.platform` 插件，目标宿主是 IntelliJ IDEA Community `2024.3.5`，插件 ID 是 `com.zimaai.codetrace`。
- 预计回答的问题：
  这是给谁用的插件？依赖哪些 IntelliJ 模块？本地怎样跑测试和插件沙箱？

### 2. 插件入口与启动流程

- 为什么读：
  这是理解「插件何时初始化」和「控制器从哪里来」的最短路径。
- 重点阅读：
  `src/main/resources/META-INF/plugin.xml`
  `src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceToolWindowFactory.java`
  `src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceProjectService.java`
- 你将理解：
  `plugin.xml` 注册了一个 project service 和一个 Tool Window；`CodeTraceProjectService` 在项目级组装 `TraceStorageService`、`CodeNavigationService`、`CodeTraceController`；`CodeTraceToolWindowFactory` 负责创建 `CodeTracePanel` 并注册刷新回调。
- 预计回答的问题：
  项目级单例是谁？Tool Window 打开时做了哪些初始化？外部动作如何刷新现有面板？

### 3. 顶层目录与模块分工

- 为什么读：
  先建立包级认知，再深入方法级调用链，阅读成本最低。
- 重点阅读：
  `src/main/java/com/zimaai/codetrace/actions`
  `src/main/java/com/zimaai/codetrace/toolwindow`
  `src/main/java/com/zimaai/codetrace/storage`
  `src/main/java/com/zimaai/codetrace/navigation`
  `src/main/java/com/zimaai/codetrace/model`
- 你将理解：
  `actions` 负责从编辑器/Psi 采集节点并触发写入；`toolwindow` 负责控制器、状态与 Swing UI；`storage` 负责 `code-trace` 目录和 JSON 读写；`navigation` 负责路径解析与打开文件；`model` 定义 `TraceDocument`、`TraceNode`、`TraceLink`。
- 预计回答的问题：
  哪些类是纯模型？哪些类带 IntelliJ 依赖？未来新增功能应该落在哪一层？

### 4. 编辑器动作到 trace 写入的主流程

- 为什么读：
  这是项目最关键的业务链路，决定「Add to code-trace」到底做了什么。
- 重点阅读：
  `src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceAction.java`
  `src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceHandler.java`
  `src/main/java/com/zimaai/codetrace/actions/PsiTraceNodeCaptureService.java`
  `src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java`
- 你将理解：
  右键动作只在已选中 trace 文件时可用；`AddToCodeTraceAction` 创建 handler；handler 用 `PsiTraceNodeCaptureService` 采集当前节点并尝试探测目标调用，再通过 `CodeTraceController` 追加或复用节点、建立 `DETECTED` 链接，并触发 Tool Window 刷新。
- 预计回答的问题：
  节点是怎样从 caret 位置推导出来的？为什么外部文件不会被加入 trace？自动连线的触发条件是什么？

### 5. Tool Window UI 与控制层

- 为什么读：
  这个部分承接用户的大多数可见行为，也是后续改 UI、改交互、加命令时最容易碰到的区域。
- 重点阅读：
  `src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
  `src/main/java/com/zimaai/codetrace/toolwindow/TraceFileListPanel.java`
  `src/main/java/com/zimaai/codetrace/toolwindow/TraceEditorPanel.java`
  `src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java`
  `src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceState.java`
  `src/main/java/com/zimaai/codetrace/toolwindow/TraceDocumentEditor.java`
- 你将理解：
  `CodeTracePanel` 是主 UI 组装点，负责文件列表、节点列表、按钮事件和重建视图；`CodeTraceController` 负责面向用例的状态变更；`TraceDocumentEditor` 负责对不可变文档模型做纯数据变换；`CodeTraceState` 负责当前文件、当前文档、待连线源节点等运行态状态。
- 预计回答的问题：
  UI 层与业务层的分界在哪里？为什么 controller 不直接改 Swing 组件？刷新、选中和持久化是谁驱动的？

### 6. trace 数据模型与 JSON 持久化

- 为什么读：
  任何功能扩展最终都会落到 `TraceDocument` 结构和磁盘存储格式上。
- 重点阅读：
  `src/main/java/com/zimaai/codetrace/model/TraceDocument.java`
  `src/main/java/com/zimaai/codetrace/model/TraceNode.java`
  `src/main/java/com/zimaai/codetrace/model/TraceLink.java`
  `src/main/java/com/zimaai/codetrace/model/TraceLinkKind.java`
  `src/main/java/com/zimaai/codetrace/storage/TraceJsonMapper.java`
  `src/main/java/com/zimaai/codetrace/storage/TraceStorageService.java`
  `src/main/java/com/zimaai/codetrace/storage/TraceProjectPaths.java`
- 你将理解：
  trace 文件存放在项目根目录下的 `code-trace` 目录；`TraceJsonMapper` 当前输出 `schemaVersion = 2`，并兼容旧版 schema 迁移；`TraceStorageService` 负责列举、读取、保存、复制、重命名和删除 JSON 文件；`TraceDocumentEditor` 对文档的增删改链路都保持不可变更新。
- 预计回答的问题：
  trace 文件为什么放在项目目录而不是 IDE 配置目录？ schema 演进点在哪里？ link 有哪些约束？

### 7. 导航机制与路径解析

- 为什么读：
  `code-trace` 的价值之一是从整理后的 trace 节点回到真实代码，这部分决定导航是否稳定。
- 重点阅读：
  `src/main/java/com/zimaai/codetrace/navigation/CodeNavigationService.java`
  `src/main/java/com/zimaai/codetrace/navigation/TraceNodePathResolver.java`
  `src/main/java/com/zimaai/codetrace/actions/PsiTraceNodeCaptureService.java`
- 你将理解：
  保存到 trace 中的文件路径优先是相对项目根目录的 `/` 分隔路径；导航时再解析成绝对路径并通过 `OpenFileDescriptor` 打开；项目外文件会在采集阶段被拒绝，以避免不可移植的 trace 数据。
- 预计回答的问题：
  为什么路径要标准化成 `/`？相对路径和绝对路径分别怎样处理？哪些情况会导致导航失败？

### 8. 测试结构与回归重点

- 为什么读：
  这个项目规模不大，但已经把关键行为拆成了较完整的单元测试。改动前先知道哪些测试在守什么边界。
- 重点阅读：
  `src/test/java/com/zimaai/codetrace/actions`
  `src/test/java/com/zimaai/codetrace/toolwindow`
  `src/test/java/com/zimaai/codetrace/storage`
  `src/test/java/com/zimaai/codetrace/navigation`
- 你将理解：
  测试覆盖了节点采集、外部文件拒绝、JSON 迁移、导航路径解析、controller 行为和部分 UI 面板交互；`PluginDescriptorTest` 还会守住插件描述文件是否正常。
- 预计回答的问题：
  哪些改动最应该先跑现有测试？哪些区域目前只有单元测试、缺少集成验证？

### 9. 配置、构建与发布边界

- 为什么读：
  这是理解版本兼容性、插件依赖和升级成本的边界章节。
- 重点阅读：
  `build.gradle.kts`
  `src/main/resources/META-INF/plugin.xml`
- 你将理解：
  项目当前依赖 Jackson 处理 trace JSON，依赖 IntelliJ 平台和 `com.intellij.java` bundled plugin，`sinceBuild = 243` 表示兼容 IntelliJ 2024.3 系列；测试框架依赖 IntelliJ Platform Test Framework 和 JUnit 5。
- 预计回答的问题：
  升级 IntelliJ 版本会影响什么？新增语言能力时要改 `plugin.xml` 还是 Gradle 依赖？哪些行为耦合到 Java 插件？

## 建议的实际阅读路径

如果你是第一次进这个仓库，建议按下面顺序打开代码：

1. `build.gradle.kts` + `plugin.xml`
2. `CodeTraceProjectService` + `CodeTraceToolWindowFactory`
3. `AddToCodeTraceAction` + `AddToCodeTraceHandler`
4. `PsiTraceNodeCaptureService`
5. `CodeTraceController` + `CodeTracePanel`
6. `TraceDocumentEditor` + `CodeTraceState`
7. `TraceStorageService` + `TraceJsonMapper`
8. `CodeNavigationService` + `TraceNodePathResolver`
9. 对应测试类，确认每个环节已有的行为约束

这个顺序的优点是：先看外层入口，再看控制流，再看底层数据和基础设施，不容易在细节里迷路。

## 当前仓库中与本指南相关的事实

- 这是一个基于 IntelliJ Platform 的项目级插件。
- Tool Window ID 是 `code-trace`，停靠在左侧。
- 编辑器右键菜单动作文本是 `Add to code-trace`。
- trace 数据默认存放在项目根目录下的 `code-trace/`。
- 当前源码主要集中在 Java 包 `com.zimaai.codetrace` 下，测试按同样分层组织。

## 下一步可以继续生成的章节

如果你要我继续写正文，最值得优先展开的是：

1. 第 2 章《插件入口与启动流程》
2. 第 4 章《编辑器动作到 trace 写入的主流程》
3. 第 5 章《Tool Window UI 与控制层》
4. 第 6 章《trace 数据模型与 JSON 持久化》

这 4 章覆盖了项目的主业务面，读完后基本就能开始做二次开发。
