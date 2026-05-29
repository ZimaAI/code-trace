# 第 2 章：插件入口与启动流程

## 本章要解决的问题

这一章重点回答 4 个问题：

1. `code-trace` 插件在 IntelliJ 里暴露了哪些入口。
2. 项目级共享状态是在哪里初始化并复用的。
3. Tool Window 首次打开时，UI 和 controller 是怎样接起来的。
4. 编辑器右键动作写入 trace 后，为什么已经打开的 Tool Window 能自动刷新。

如果你刚接手这个项目，这一章的价值在于先建立「插件从哪里进来、核心对象在哪里汇合」的认知。没有这层认知，后面读 `actions`、`toolwindow` 和 `storage` 时会很容易把入口、状态和 UI 的职责混在一起。

## 先看哪些文件

建议按下面顺序阅读：

1. `src/main/resources/META-INF/plugin.xml`
2. `src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceProjectService.java`
3. `src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceToolWindowFactory.java`
4. `src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`
5. `src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java`
6. `src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceAction.java`
7. `src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceHandler.java`
8. `src/test/java/com/zimaai/codetrace/toolwindow/PluginDescriptorTest.java`

这个顺序的原因是：先看声明，再看装配，再看 UI 初始化，最后再看外部动作如何复用同一套对象。

## 这一部分在项目中的职责

这一部分负责把插件的三个入口统一接到同一个项目级控制中心上：

- `plugin.xml` 声明插件对 IntelliJ 暴露的扩展点。
- `CodeTraceProjectService` 在项目级装配共享的 `CodeTraceController`。
- `CodeTraceToolWindowFactory` 在 Tool Window 首次创建时把 UI 面板接到 controller。
- `AddToCodeTraceAction` 在编辑器右键菜单中复用同一个 controller，并在需要时触发 Tool Window 刷新。

换句话说，这一层不是在做业务编辑，而是在解决「谁先初始化」「对象在哪共享」「UI 怎样和外部动作同步」这三个基础问题。

## 核心代码如何工作

### 1. `plugin.xml` 声明了 3 个真正的入口

文件：`src/main/resources/META-INF/plugin.xml`

这里有 3 个关键注册项：

- `projectService`
  - `serviceImplementation="com.zimaai.codetrace.toolwindow.CodeTraceProjectService"`
  - 含义：为每个 IntelliJ `Project` 提供一个共享 service。
- `toolWindow`
  - `id="code-trace"`
  - `factoryClass="com.zimaai.codetrace.toolwindow.CodeTraceToolWindowFactory"`
  - 含义：用户打开左侧 `code-trace` 面板时，由这个 factory 创建内容。
- `action`
  - `class="com.zimaai.codetrace.actions.AddToCodeTraceAction"`
  - `group-id="EditorPopupMenu"`
  - 含义：把 `Add to code-trace` 挂到编辑器右键菜单。

这 3 个入口分别对应：

- 项目级共享状态入口
- 面板 UI 入口
- 编辑器交互入口

从这里开始读，你会很快知道这个插件不是靠某个单一主类启动，而是通过 IntelliJ 的扩展点把不同入口分别挂进去。

### 2. `CodeTraceProjectService` 是项目级共享控制中心

文件：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceProjectService.java`

构造函数 `CodeTraceProjectService(Project project)` 做了三步装配：

1. 创建 `TraceStorageService`
   - `new TraceStorageService(project, new TraceJsonMapper())`
   - 用于访问项目根目录下的 `code-trace/*.json`。
2. 创建 `CodeNavigationService`
   - `new CodeNavigationService(project)`
   - 用于把 `TraceNode` 导航回真实源码位置。
3. 创建 `CodeTraceController`
   - `new CodeTraceController(storage, navigation::navigate)`
   - 把存储和导航能力组合成统一的业务控制入口。

这里最关键的设计点不是「new 了几个对象」，而是 `CodeTraceController` 只在项目级创建一次。后续 Tool Window 和右键动作都通过 `project.getService(CodeTraceProjectService.class)` 拿到同一个 controller。

这意味着这些运行时状态是共享的：

- 当前选中的 trace 文件
- 当前加载的 trace 文档
- 待连线节点状态
- 偏好的选中节点状态

如果没有这个 project-scoped service，Tool Window 和编辑器动作就会各自维护一份状态，行为很快会分叉。

### 3. Tool Window 首次创建时的初始化顺序很短，但很关键

文件：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceToolWindowFactory.java`

方法：`createToolWindowContent(Project project, ToolWindow toolWindow)`

执行顺序如下：

1. `project.getService(CodeTraceProjectService.class)`
2. `new CodeTracePanel(service.controller())`
3. `service.registerRefreshCallback(panel::refreshFromExternalAction)`
4. `panel.reloadFromDisk()`
5. `ContentFactory.getInstance().createContent(...)`
6. `toolWindow.getContentManager().addContent(content)`

这几步里最重要的是第 3 步和第 4 步。

`registerRefreshCallback(panel::refreshFromExternalAction)` 的作用：

- 把当前面板实例的刷新逻辑登记到 project service 上。
- 以后外部动作只要拿到 `CodeTraceProjectService`，就能通过 `refreshToolWindowIfPresent()` 请求 UI 刷新。
- 外部动作不需要知道 `CodeTracePanel` 是谁，也不需要持有 UI 引用。

`panel.reloadFromDisk()` 的作用：

- 首次打开 Tool Window 时，确保面板有内容可显示。
- 它会调用 `CodeTraceController.ensureAnyFileLoaded()`。
- 如果 `code-trace` 目录里已经有 JSON 文件，就加载第一个。
- 如果一个都没有，就创建一个新的 trace 文件。
- 之后再 `rebuildView()`，把界面和当前文档状态对齐。

这条初始化链路说明了一件事：Tool Window 不是单纯渲染一个空面板，而是首次打开时就负责把项目中的 trace 状态拉起来。

### 4. `reloadFromDisk()` 和 `refreshFromExternalAction()` 是两条不同的刷新路径

文件：`src/main/java/com/zimaai/codetrace/toolwindow/CodeTracePanel.java`

两个方法分别是：

- `reloadFromDisk()`
  - `controller.ensureAnyFileLoaded()`
  - `rebuildView()`
- `refreshFromExternalAction()`
  - `controller.refreshCurrentFile()`
  - `rebuildView()`

它们的差异很重要：

- `reloadFromDisk()` 面向首次创建或兜底场景
  - 它保证「一定有当前文件」。
  - 必要时会创建新 trace 文件。
- `refreshFromExternalAction()` 面向已有面板、已有当前文件的场景
  - 它只重载当前文件，不负责创建新文件。

这个分工避免了外部动作每次刷新都触发初始化分支，也避免了首次打开面板时因为当前文件为空而出现空界面。

### 5. 右键动作复用了同一个 project service，而不是另起一套状态

文件：`src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceAction.java`

`update(AnActionEvent event)` 会检查：

- 当前有 `Project`
- 当前有 `Editor`
- `project.getService(CodeTraceProjectService.class).controller().state().currentFileName() != null`

这说明右键动作只有在「当前已经选中一个 trace 文件」时才会启用。实际效果是：用户通常需要先打开一次 Tool Window，让 controller 进入已加载状态。

`actionPerformed(AnActionEvent event)` 的主链路是：

1. 取出 `Project` 和 `Editor`
2. 通过 `PsiDocumentManager` 拿到 `PsiFile`
3. 取出 `CodeTraceProjectService`
4. 构造 `AddToCodeTraceHandler`
   - 传入 `service.controller()`
   - 传入 `new PsiTraceNodeCaptureService()`
   - 传入 `new SwingTraceUserPrompts()`
   - 传入 `service::refreshToolWindowIfPresent`
5. 调用 `handler.handle(project, editor, psiFile)`

这里最重要的不是 handler 本身，而是两个复用点：

- 它没有自己创建 `CodeTraceController`
- 它把 `service::refreshToolWindowIfPresent` 当作回调传了进去

因此它和 Tool Window 操作的是同一份运行时状态。

### 6. 外部动作刷新现有 Tool Window 的机制是「可选回调」

文件：

- `src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceProjectService.java`
- `src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceToolWindowFactory.java`
- `src/main/java/com/zimaai/codetrace/actions/AddToCodeTraceAction.java`

刷新链路可以压缩成下面这条：

1. Tool Window 创建时：
   - `CodeTraceToolWindowFactory.createToolWindowContent(...)`
   - `-> service.registerRefreshCallback(panel::refreshFromExternalAction)`
2. 编辑器动作执行时：
   - `AddToCodeTraceAction.actionPerformed(...)`
   - `-> new AddToCodeTraceHandler(..., service::refreshToolWindowIfPresent)`
3. handler 写入 trace 后：
   - 调用 `refreshToolWindowIfPresent()`
4. `CodeTraceProjectService.refreshToolWindowIfPresent()`
   - 如果 `refreshCallback != null`，执行 `refreshCallback.run()`
5. 实际运行的方法是：
   - `CodeTracePanel.refreshFromExternalAction()`
   - `-> controller.refreshCurrentFile()`
   - `-> rebuildView()`

这套机制的优点是边界很干净：

- 编辑器动作不直接依赖 `CodeTracePanel`
- Tool Window 是否已存在，由 `refreshCallback` 是否已经注册决定
- 如果 Tool Window 还没打开，刷新调用会静默跳过，不会报错

这是一个典型的「共享控制层 + UI 可选同步」设计。

## 关键类型 / 函数 / 类 / 配置

### 配置入口

- `src/main/resources/META-INF/plugin.xml`
  - `projectService`
  - `toolWindow`
  - `action`

### 项目级装配

- `CodeTraceProjectService`
  - `CodeTraceProjectService(Project project)`
  - `controller()`
  - `registerRefreshCallback(Runnable refreshCallback)`
  - `refreshToolWindowIfPresent()`

### Tool Window 初始化

- `CodeTraceToolWindowFactory`
  - `createToolWindowContent(Project project, ToolWindow toolWindow)`

### UI 刷新入口

- `CodeTracePanel`
  - `reloadFromDisk()`
  - `refreshFromExternalAction()`

### 控制层兜底加载

- `CodeTraceController`
  - `ensureAnyFileLoaded()`
  - `refreshCurrentFile()`

### 外部动作入口

- `AddToCodeTraceAction`
  - `update(AnActionEvent event)`
  - `actionPerformed(AnActionEvent event)`

## 典型调用链或执行流程

### 流程 1：用户首次打开 Tool Window

`plugin.xml`
-> `CodeTraceToolWindowFactory.createToolWindowContent(...)`
-> `project.getService(CodeTraceProjectService.class)`
-> `new CodeTracePanel(service.controller())`
-> `service.registerRefreshCallback(panel::refreshFromExternalAction)`
-> `panel.reloadFromDisk()`
-> `controller.ensureAnyFileLoaded()`
-> `rebuildView()`

### 流程 2：用户在编辑器里执行「Add to code-trace」

`plugin.xml`
-> `AddToCodeTraceAction.actionPerformed(...)`
-> `project.getService(CodeTraceProjectService.class)`
-> `new AddToCodeTraceHandler(..., service::refreshToolWindowIfPresent)`
-> `handler.handle(...)`
-> `service.refreshToolWindowIfPresent()`
-> `panel.refreshFromExternalAction()`
-> `controller.refreshCurrentFile()`
-> `rebuildView()`

## 相关测试如何守住这一层

推荐先看：

- `src/test/java/com/zimaai/codetrace/toolwindow/PluginDescriptorTest.java`
- `src/test/java/com/zimaai/codetrace/toolwindow/CodeTracePanelTest.java`

`PluginDescriptorTest` 主要验证：

- `plugin.xml` 文件存在
- 插件 ID 正确
- `code-trace` Tool Window 已注册
- `CodeTraceToolWindowFactory` 已注册
- `CodeTraceProjectService` 已注册
- `AddToCodeTraceAction` 已注册到 `EditorPopupMenu`

这能防止最基础的扩展点声明被改坏。

`CodeTracePanelTest` 虽然不直接验证启动流程，但它守住了 Tool Window 顶部工具栏和编辑区按钮的基本布局边界。对理解「UI 初始化后应该呈现什么结构」也有帮助。

## 阅读这一章后你应该理解什么

读完这一章，你应该能清楚说出下面这些事实：

- 插件的三个入口是 `projectService`、`toolWindow` 和编辑器右键 `action`。
- `CodeTraceProjectService` 是项目级共享装配点，里面持有唯一的 `CodeTraceController`。
- Tool Window 打开时，不是随便 new 一个 UI，而是把面板绑定到已经存在的 project-scoped controller 上。
- `reloadFromDisk()` 解决首次加载问题，`refreshFromExternalAction()` 解决外部动作后的同步问题。
- 编辑器右键动作和 Tool Window 共享的是同一份 controller/state，而不是两套彼此独立的状态。

如果这 5 点已经建立，你再去读节点采集、trace 持久化或 UI 编辑逻辑时，基本不会迷路。

## 下一步建议阅读

下一章最建议直接读第 4 章《编辑器动作到 trace 写入的主流程》，因为第 2 章已经把入口和共享状态讲清楚了，接下来正好沿着这条链路深入：

- `AddToCodeTraceAction`
- `AddToCodeTraceHandler`
- `PsiTraceNodeCaptureService`
- `CodeTraceController`

如果你更关心界面层，也可以先跳到第 5 章《Tool Window UI 与控制层》。
