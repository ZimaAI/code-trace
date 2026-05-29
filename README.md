# code-trace

![Java 21](https://img.shields.io/badge/Java-21-blue)

`code-trace` 是一个 IntelliJ Platform 插件，配套一个 `code-trace-reading` skill。插件负责在 IDE 中采集、编辑和跳转 trace 节点；skill 负责让 AI 助手按同一套 JSON schema 生成或补全 `code-trace/*.json` 文件。

## 核心能力

- 在编辑器右键菜单中通过 `Add to code-trace` 采集当前位置，并在可检测时自动补上目标节点。
- 在左侧 `code-trace` Tool Window 中管理 trace 文件，支持创建、复制、重命名、删除、刷新和切换当前 trace。
- 编辑 trace 描述、node note、节点顺序与 source/target 链路，并在 `Go to Source`、`Go to Target` 或双击节点时回到源码。
- 通过 `code-trace-reading` skill 生成高质量 trace，并用 `trace-tools.js` 校验 schema。

## 快速开始

### 前提

- JDK `21`
- Gradle Wrapper（仓库已提供 `gradlew` 与 `gradlew.bat`）
- （可选）Node.js，用于执行 `code-trace-reading` 的 `trace-tools.js`

### 启动插件沙箱

```bash
./gradlew runIde
```

Windows 使用 `./gradlew.bat runIde`。

在 sandbox IDE 中：

1. 打开任意项目。
2. 打开左侧 `code-trace` Tool Window。首次加载时会读取或创建项目根目录下的 `code-trace/*.json`。
3. 在编辑器中右键选择 `Add to code-trace`，把当前节点加入当前 trace。

## 配套 skill

`code-trace-reading` 位于 `.agents/skills/code-trace-reading/`，用于让 AI 助手直接产出插件可打开的 trace 文件。

- 默认提示词定义在 `.agents/skills/code-trace-reading/agents/openai.yaml`。
- trace 文件 schema 定义在 `.agents/skills/code-trace-reading/references/trace-schema.md`。
- `trace-tools.js` 提供 `new` 与 `validate` 两个入口，用于创建和校验 trace 文件。
- 仓库中的 `code-trace/20260529-代码跳转实现原理.json` 提供了一个完整示例。

示例提示词：

```text
Use $code-trace-reading to analyze a feature in this project and produce a code-trace JSON with detailed node notes.
```

## 项目结构

```text
code-trace/
├── src/main/java/com/zimaai/codetrace/
│   ├── actions/                            # 编辑器右键采集、目标检测与用户提示
│   ├── model/                              # TraceDocument / TraceNode / TraceLink 数据模型
│   ├── navigation/                         # 路径解析与源码跳转
│   ├── storage/                            # code-trace 目录与 JSON 读写
│   └── toolwindow/                         # Tool Window、控制器与 Swing UI
├── src/main/resources/META-INF/plugin.xml  # 插件注册入口
├── src/test/java/com/zimaai/codetrace/     # actions/toolwindow/storage/navigation 测试
├── .agents/skills/code-trace-reading/      # 配套 skill、schema 与 trace 校验脚本
├── code-trace/                             # 示例 trace 文件
└── docs/tutorial/project-onboarding-guide/ # 面向开发者的源码阅读指南
```

第一次进入仓库，建议先读 `docs/tutorial/project-onboarding-guide/README.md`。

## 常用命令

```bash
./gradlew runIde
./gradlew test
./gradlew buildPlugin
./gradlew verifyPlugin
node ./.agents/skills/code-trace-reading/scripts/trace-tools.js validate --input ./code-trace/20260529-代码跳转实现原理.json
```

Gradle 任务来自根目录 `build.gradle.kts` 与 Gradle Wrapper，trace 校验命令来自 `.agents/skills/code-trace-reading/scripts/trace-tools.js`。

## 配置与环境

- 插件注册信息在 `src/main/resources/META-INF/plugin.xml`：`projectService` 为 `CodeTraceProjectService`，Tool Window ID 为 `code-trace`，编辑器右键动作文本为 `Add to code-trace`。
- trace 数据默认存放在项目根目录下的 `code-trace/`，由 `TraceStorageService` 负责列举、读取、保存、复制、重命名和删除。
- 节点 `filePath` 优先保存为相对项目根目录的路径，导航时由 `TraceNodePathResolver` 还原为可打开的源码路径。
- 仓库中未找到 `.env.example` 或 `config/` 目录；当前没有额外环境变量要求。

## 测试与质量

- 插件测试主要覆盖 `actions`、`toolwindow`、`storage` 和 `navigation` 四层，重点验证节点采集、JSON 迁移、路径解析和 UI/controller 行为。
- `TraceJsonMapper` 当前写出 `schemaVersion = 2`，并会在读取时迁移旧的 schema 1 文档。
- 如果只想检查 trace 文件格式，优先运行 `trace-tools.js validate`；如果要回归插件行为，优先运行 `./gradlew test`。

## 文档维护

当下列内容发生变化时，请同步更新 README：

- `plugin.xml` 中的插件入口、Tool Window 或 action 发生变化。
- `build.gradle.kts` 中的 Java / IntelliJ 版本或常用 Gradle 任务发生变化。
- `code-trace-reading` 的 schema、默认提示词或 `trace-tools.js` 命令发生变化。
- `code-trace/` 目录约定、文件命名规则或示例 trace 发生变化。