# Code Trace Reading 生成规则实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 更新 `code-trace-reading` skill、schema 参考和辅助脚本，使新建 trace 默认采用 `yyyyMMdd-主题.json` 命名，并对关键调用的 source/target 成对记录和 link 唯一性提供稳定约束。

**架构：** `trace-tools.js` 继续作为无依赖的 CommonJS CLI 脚本，但内部拆成更清晰的“文件名生成”和“文档校验”两类辅助函数。测试采用 Node 24 自带的 `node:test` 黑盒调用脚本，不引入 `package.json` 或第三方测试框架；文档层通过更新 skill 和 schema 参考来收敛人工操作规则。

**技术栈：** Markdown、Node.js 24（CommonJS、`node:test`）、PowerShell

---

## Planned File Structure

### CLI behavior and semantic validation

- 修改：`.agents/skills/code-trace-reading/scripts/trace-tools.js`
  - 保留 `new` / `validate` 两个命令入口。
  - 为 `new` 增加默认文件名推导、中文主题最小清洗、同名去重和 `--trace-dir` 支持。
  - 为 `validate` 增加 “一个 source 最多一个 target” 与自环检查，同时保留现有 schema 校验。

- 创建：`.agents/skills/code-trace-reading/scripts/trace-tools.test.js`
  - 使用 `node:test` 以黑盒方式调用 `trace-tools.js`。
  - 覆盖默认命名、中文主题、非法字符清洗、重名去重、source 多 target 拒绝、target 多 source 允许、自环拒绝。

### Skill and schema guidance

- 修改：`.agents/skills/code-trace-reading/SKILL.md`
  - 将默认新建文件策略改为 `yyyyMMdd-主题.json`。
  - 将“关键调用成对记录”和“source 最多一个 target”写成明确工作流规则。
  - 补充更新已有 trace 时只修本次改动链路的边界。

- 修改：`.agents/skills/code-trace-reading/references/trace-schema.md`
  - 增补新建文件命名规范。
  - 增补 source/target 节点对、link 唯一性和调用对示例。
  - 明确旧文件名继续兼容，不把历史 `trace-*.json` 直接视为非法。

## Task 1: 先用黑盒测试锁定默认命名行为，再实现 `new` 命令

**文件：**
- 修改：`.agents/skills/code-trace-reading/scripts/trace-tools.js`
- 创建：`.agents/skills/code-trace-reading/scripts/trace-tools.test.js`

- [ ] **步骤 1：编写失败的 `node:test` 黑盒用例**

创建 `.agents/skills/code-trace-reading/scripts/trace-tools.test.js`：

```js
const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { spawnSync } = require("node:child_process");

const scriptPath = path.resolve(__dirname, "trace-tools.js");

function runTraceTools(args, options = {}) {
  return spawnSync(process.execPath, [scriptPath, ...args], {
    encoding: "utf8",
    ...options,
  });
}

function makeTempDir() {
  return fs.mkdtempSync(path.join(os.tmpdir(), "trace-tools-"));
}

test("new derives a yyyyMMdd-topic filename under trace-dir when output is omitted", () => {
  const traceDir = makeTempDir();
  const result = runTraceTools([
    "new",
    "--name",
    "登录流程",
    "--description",
    "trace summary",
    "--trace-dir",
    traceDir,
  ]);

  assert.equal(result.status, 0, result.stderr);
  const files = fs.readdirSync(traceDir);
  assert.equal(files.length, 1);
  assert.match(files[0], /^\d{8}-登录流程\.json$/);

  const stored = JSON.parse(fs.readFileSync(path.join(traceDir, files[0]), "utf8"));
  assert.equal(stored.name, "登录流程");
  assert.equal(stored.description, "trace summary");
});

test("new sanitizes invalid filename characters but preserves Chinese topic text", () => {
  const traceDir = makeTempDir();
  const result = runTraceTools([
    "new",
    "--name",
    "登录/流程:保存?",
    "--trace-dir",
    traceDir,
  ]);

  assert.equal(result.status, 0, result.stderr);
  const files = fs.readdirSync(traceDir);
  assert.deepEqual(files.length, 1);
  assert.match(files[0], /^\d{8}-登录-流程-保存\.json$/);
});

test("new falls back to 未命名主题 when cleaning leaves an empty topic", () => {
  const traceDir = makeTempDir();
  const result = runTraceTools([
    "new",
    "--name",
    "<>:\"/\\\\|?*",
    "--trace-dir",
    traceDir,
  ]);

  assert.equal(result.status, 0, result.stderr);
  const files = fs.readdirSync(traceDir);
  assert.equal(files.length, 1);
  assert.match(files[0], /^\d{8}-未命名主题\.json$/);
});

test("new appends a numeric suffix when the same-day topic filename already exists", () => {
  const traceDir = makeTempDir();

  const first = runTraceTools(["new", "--name", "登录流程", "--trace-dir", traceDir]);
  assert.equal(first.status, 0, first.stderr);

  const second = runTraceTools(["new", "--name", "登录流程", "--trace-dir", traceDir]);
  assert.equal(second.status, 0, second.stderr);

  const files = fs.readdirSync(traceDir).sort();
  assert.equal(files.length, 2);
  assert.match(files[0], /^\d{8}-登录流程\.json$/);
  assert.match(files[1], /^\d{8}-登录流程-2\.json$/);
});
```

- [ ] **步骤 2：运行测试验证当前脚本尚未支持这些行为**

运行：

```powershell
node --test .agents\skills\code-trace-reading\scripts\trace-tools.test.js
```

预期：

- 至少 3 个测试失败。
- 失败原因包括：`new` 仍要求显式 `--output`，不识别 `--trace-dir`，也不会生成 `yyyyMMdd-主题.json`。

- [ ] **步骤 3：实现最小命名能力，不改变现有 CLI 的基本结构**

把 `.agents/skills/code-trace-reading/scripts/trace-tools.js` 调整为下面这个形态：

```js
#!/usr/bin/env node

const fs = require("fs");
const path = require("path");
const crypto = require("crypto");

const LINK_KINDS = new Set(["MANUAL", "DETECTED"]);
const INVALID_FILE_NAME_CHARS = /[\\/:*?"<>|]+/g;

function fail(message) {
  console.error(message);
  process.exit(1);
}

function parseArgs(argv) {
  const [command, ...rest] = argv;
  const options = {};
  for (let index = 0; index < rest.length; index += 1) {
    const token = rest[index];
    if (!token.startsWith("--")) {
      fail(`Unexpected argument: ${token}`);
    }
    const key = token.slice(2);
    const value = rest[index + 1];
    if (value == null || value.startsWith("--")) {
      options[key] = true;
      continue;
    }
    options[key] = value;
    index += 1;
  }
  return { command, options };
}

function ensureString(value, field, errors) {
  if (typeof value !== "string" || value.length === 0) {
    errors.push(`${field} must be a non-empty string`);
  }
}

function ensureIsoInstant(value, field, errors) {
  if (typeof value !== "string" || Number.isNaN(Date.parse(value))) {
    errors.push(`${field} must be an ISO-8601 timestamp string`);
  }
}

function formatDateStamp(now) {
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}${month}${day}`;
}

function sanitizeTopic(topic) {
  const cleaned = topic
    .trim()
    .replace(INVALID_FILE_NAME_CHARS, "-")
    .replace(/\s+/g, " ")
    .replace(/-+/g, "-")
    .replace(/^[ .-]+|[ .-]+$/g, "");
  return cleaned || "未命名主题";
}

function buildTraceFileName(topic, now, existingNames) {
  const dateStamp = formatDateStamp(now);
  const safeTopic = sanitizeTopic(topic);
  const baseName = `${dateStamp}-${safeTopic}`;
  let candidate = `${baseName}.json`;
  let suffix = 2;
  while (existingNames.has(candidate)) {
    candidate = `${baseName}-${suffix}.json`;
    suffix += 1;
  }
  return candidate;
}

function resolveOutputPath(options, now) {
  if (options.output) {
    return path.resolve(options.output);
  }
  const traceDir = path.resolve(options["trace-dir"] || path.join(process.cwd(), "code-trace"));
  fs.mkdirSync(traceDir, { recursive: true });
  const existingNames = new Set(
    fs.readdirSync(traceDir, { withFileTypes: true })
      .filter((entry) => entry.isFile())
      .map((entry) => entry.name)
  );
  return path.join(traceDir, buildTraceFileName(options.name, now, existingNames));
}

function validateNode(node, index, ids, errors) {
  const prefix = `nodes[${index}]`;
  if (node == null || typeof node !== "object" || Array.isArray(node)) {
    errors.push(`${prefix} must be an object`);
    return;
  }
  ensureString(node.id, `${prefix}.id`, errors);
  ensureString(node.displayName, `${prefix}.displayName`, errors);
  ensureString(node.qualifiedName, `${prefix}.qualifiedName`, errors);
  ensureString(node.signature, `${prefix}.signature`, errors);
  ensureString(node.filePath, `${prefix}.filePath`, errors);
  ensureString(node.language, `${prefix}.language`, errors);
  if (typeof node.note !== "string") {
    errors.push(`${prefix}.note must be a string`);
  }
  ensureString(node.navigationHint, `${prefix}.navigationHint`, errors);
  if (!Number.isInteger(node.line) || node.line < 1) {
    errors.push(`${prefix}.line must be an integer greater than or equal to 1`);
  }
  if (typeof node.id === "string") {
    if (ids.has(node.id)) {
      errors.push(`${prefix}.id duplicates an earlier node id: ${node.id}`);
    }
    ids.add(node.id);
  }
}

function validateLink(link, index, nodeIds, linkIds, errors) {
  const prefix = `links[${index}]`;
  if (link == null || typeof link !== "object" || Array.isArray(link)) {
    errors.push(`${prefix} must be an object`);
    return;
  }
  ensureString(link.id, `${prefix}.id`, errors);
  ensureString(link.sourceNodeId, `${prefix}.sourceNodeId`, errors);
  ensureString(link.targetNodeId, `${prefix}.targetNodeId`, errors);
  ensureIsoInstant(link.createdAt, `${prefix}.createdAt`, errors);
  if (!LINK_KINDS.has(link.kind)) {
    errors.push(`${prefix}.kind must be MANUAL or DETECTED`);
  }
  if (typeof link.id === "string") {
    if (linkIds.has(link.id)) {
      errors.push(`${prefix}.id duplicates an earlier link id: ${link.id}`);
    }
    linkIds.add(link.id);
  }
  if (typeof link.sourceNodeId === "string" && !nodeIds.has(link.sourceNodeId)) {
    errors.push(`${prefix}.sourceNodeId does not reference an existing node: ${link.sourceNodeId}`);
  }
  if (typeof link.targetNodeId === "string" && !nodeIds.has(link.targetNodeId)) {
    errors.push(`${prefix}.targetNodeId does not reference an existing node: ${link.targetNodeId}`);
  }
}

function validateDocument(document) {
  const errors = [];
  if (document == null || typeof document !== "object" || Array.isArray(document)) {
    return ["Document must be a JSON object"];
  }

  if (document.schemaVersion !== 2) {
    errors.push("schemaVersion must be 2");
  }
  ensureString(document.id, "id", errors);
  ensureString(document.name, "name", errors);
  if (typeof document.description !== "string") {
    errors.push("description must be a string");
  }
  ensureIsoInstant(document.createdAt, "createdAt", errors);
  ensureIsoInstant(document.updatedAt, "updatedAt", errors);
  if (!Array.isArray(document.nodes)) {
    errors.push("nodes must be an array");
  }
  if (!Array.isArray(document.links)) {
    errors.push("links must be an array");
  }

  const nodeIds = new Set();
  const linkIds = new Set();
  if (Array.isArray(document.nodes)) {
    document.nodes.forEach((node, index) => validateNode(node, index, nodeIds, errors));
  }
  if (Array.isArray(document.links)) {
    document.links.forEach((link, index) => validateLink(link, index, nodeIds, linkIds, errors));
  }

  return errors;
}

function writeJson(outputPath, document) {
  const targetPath = path.resolve(outputPath);
  fs.mkdirSync(path.dirname(targetPath), { recursive: true });
  fs.writeFileSync(targetPath, `${JSON.stringify(document, null, 2)}\n`, "utf8");
  return targetPath;
}

function createDocument(options) {
  if (!options.name) {
    fail("Usage: node trace-tools.js new --name <trace name> [--description <text>] [--output <file>] [--trace-dir <dir>]");
  }
  const now = new Date();
  const outputPath = resolveOutputPath(options, now);
  const instant = now.toISOString();
  const document = {
    schemaVersion: 2,
    id: options.id || `trace-${crypto.randomUUID()}`,
    name: options.name,
    description: options.description || "",
    createdAt: instant,
    updatedAt: instant,
    nodes: [],
    links: [],
  };
  const targetPath = writeJson(outputPath, document);
  console.log(`Created ${targetPath}`);
}

function validateFile(options) {
  if (!options.input) {
    fail("Usage: node trace-tools.js validate --input <file>");
  }
  const inputPath = path.resolve(options.input);
  let document;
  try {
    document = JSON.parse(fs.readFileSync(inputPath, "utf8"));
  } catch (error) {
    fail(`Failed to read JSON from ${inputPath}: ${error.message}`);
  }

  const errors = validateDocument(document);
  if (errors.length > 0) {
    errors.forEach((error) => console.error(`- ${error}`));
    process.exit(1);
  }

  console.log(`Valid trace: ${inputPath} (${document.nodes.length} nodes, ${document.links.length} links)`);
}

function main() {
  const { command, options } = parseArgs(process.argv.slice(2));
  if (command === "new") {
    createDocument(options);
    return;
  }
  if (command === "validate") {
    validateFile(options);
    return;
  }
  fail("Usage: node trace-tools.js <new|validate> [options]");
}

main();
```

- [ ] **步骤 4：运行测试确认命名行为通过**

运行：

```powershell
node --test .agents\skills\code-trace-reading\scripts\trace-tools.test.js
```

预期：前 4 个 `new` 命令相关测试通过。

- [ ] **步骤 5：Commit**

```powershell
git add .agents/skills/code-trace-reading/scripts/trace-tools.js .agents/skills/code-trace-reading/scripts/trace-tools.test.js
git commit -m "feat(code-trace-reading): 支持按主题生成 trace 文件名"
```

## Task 2: 先补失败用例，再收紧 `validate` 的 source/target 约束

**文件：**
- 修改：`.agents/skills/code-trace-reading/scripts/trace-tools.js`
- 修改：`.agents/skills/code-trace-reading/scripts/trace-tools.test.js`

- [ ] **步骤 1：在测试文件中追加失败的 link 语义用例**

把 `.agents/skills/code-trace-reading/scripts/trace-tools.test.js` 追加为：

```js
function writeJson(filePath, value) {
  fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

function traceNode(id, displayName) {
  return {
    id,
    displayName,
    qualifiedName: id,
    signature: displayName,
    filePath: "src/main/java/demo/Sample.java",
    line: 1,
    language: "JAVA",
    note: "",
    navigationHint: id,
  };
}

function traceLink(id, sourceNodeId, targetNodeId) {
  return {
    id,
    sourceNodeId,
    targetNodeId,
    createdAt: "2026-05-29T10:00:00Z",
    kind: "MANUAL",
  };
}

function traceDocument(links) {
  return {
    schemaVersion: 2,
    id: "trace-1",
    name: "登录流程",
    description: "trace summary",
    createdAt: "2026-05-29T10:00:00Z",
    updatedAt: "2026-05-29T10:00:00Z",
    nodes: [
      traceNode("node-source", "TraceDocument document = storage.load(fileName);"),
      traceNode("node-target-a", "public TraceDocument load(String fileName) {"),
      traceNode("node-target-b", "public TraceDocument loadBackup(String fileName) {"),
      traceNode("node-source-2", "TraceDocument another = storage.load(fileName);"),
    ],
    links,
  };
}

test("validate allows multiple sources to point to the same target", () => {
  const tempDir = makeTempDir();
  const inputPath = path.join(tempDir, "valid-trace.json");
  writeJson(inputPath, traceDocument([
    traceLink("link-1", "node-source", "node-target-a"),
    traceLink("link-2", "node-source-2", "node-target-a"),
  ]));

  const result = runTraceTools(["validate", "--input", inputPath]);

  assert.equal(result.status, 0, result.stderr);
  assert.match(result.stdout, /Valid trace:/);
});

test("validate rejects a source node that points to multiple targets", () => {
  const tempDir = makeTempDir();
  const inputPath = path.join(tempDir, "invalid-trace.json");
  writeJson(inputPath, traceDocument([
    traceLink("link-1", "node-source", "node-target-a"),
    traceLink("link-2", "node-source", "node-target-b"),
  ]));

  const result = runTraceTools(["validate", "--input", inputPath]);

  assert.equal(result.status, 1);
  assert.match(result.stderr, /source node node-source links to multiple targets/);
});

test("validate rejects self-links", () => {
  const tempDir = makeTempDir();
  const inputPath = path.join(tempDir, "self-link.json");
  writeJson(inputPath, traceDocument([
    traceLink("link-1", "node-source", "node-source"),
  ]));

  const result = runTraceTools(["validate", "--input", inputPath]);

  assert.equal(result.status, 1);
  assert.match(result.stderr, /links\[0\] must not link a node to itself/);
});
```

- [ ] **步骤 2：运行测试确认当前 `validate` 还没有这些语义检查**

运行：

```powershell
node --test .agents\skills\code-trace-reading\scripts\trace-tools.test.js
```

预期：

- `validate rejects a source node that points to multiple targets` 失败，因为当前脚本还会返回成功。
- `validate rejects self-links` 失败，因为当前脚本还没有自环检查。

- [ ] **步骤 3：在 `trace-tools.js` 中补上最小语义校验**

把 `.agents/skills/code-trace-reading/scripts/trace-tools.js` 的 link 校验部分改成下面这个形态：

```js
function validateLink(link, index, nodeIds, linkIds, sourceTargets, errors) {
  const prefix = `links[${index}]`;
  if (link == null || typeof link !== "object" || Array.isArray(link)) {
    errors.push(`${prefix} must be an object`);
    return;
  }
  ensureString(link.id, `${prefix}.id`, errors);
  ensureString(link.sourceNodeId, `${prefix}.sourceNodeId`, errors);
  ensureString(link.targetNodeId, `${prefix}.targetNodeId`, errors);
  ensureIsoInstant(link.createdAt, `${prefix}.createdAt`, errors);
  if (!LINK_KINDS.has(link.kind)) {
    errors.push(`${prefix}.kind must be MANUAL or DETECTED`);
  }
  if (typeof link.id === "string") {
    if (linkIds.has(link.id)) {
      errors.push(`${prefix}.id duplicates an earlier link id: ${link.id}`);
    }
    linkIds.add(link.id);
  }
  if (typeof link.sourceNodeId === "string" && typeof link.targetNodeId === "string") {
    if (link.sourceNodeId === link.targetNodeId) {
      errors.push(`${prefix} must not link a node to itself`);
    }

    const targets = sourceTargets.get(link.sourceNodeId) || new Set();
    targets.add(link.targetNodeId);
    sourceTargets.set(link.sourceNodeId, targets);
  }
  if (typeof link.sourceNodeId === "string" && !nodeIds.has(link.sourceNodeId)) {
    errors.push(`${prefix}.sourceNodeId does not reference an existing node: ${link.sourceNodeId}`);
  }
  if (typeof link.targetNodeId === "string" && !nodeIds.has(link.targetNodeId)) {
    errors.push(`${prefix}.targetNodeId does not reference an existing node: ${link.targetNodeId}`);
  }
}

function validateDocument(document) {
  const errors = [];
  if (document == null || typeof document !== "object" || Array.isArray(document)) {
    return ["Document must be a JSON object"];
  }

  if (document.schemaVersion !== 2) {
    errors.push("schemaVersion must be 2");
  }
  ensureString(document.id, "id", errors);
  ensureString(document.name, "name", errors);
  if (typeof document.description !== "string") {
    errors.push("description must be a string");
  }
  ensureIsoInstant(document.createdAt, "createdAt", errors);
  ensureIsoInstant(document.updatedAt, "updatedAt", errors);
  if (!Array.isArray(document.nodes)) {
    errors.push("nodes must be an array");
  }
  if (!Array.isArray(document.links)) {
    errors.push("links must be an array");
  }

  const nodeIds = new Set();
  const linkIds = new Set();
  const sourceTargets = new Map();

  if (Array.isArray(document.nodes)) {
    document.nodes.forEach((node, index) => validateNode(node, index, nodeIds, errors));
  }
  if (Array.isArray(document.links)) {
    document.links.forEach((link, index) => {
      validateLink(link, index, nodeIds, linkIds, sourceTargets, errors);
    });
  }

  for (const [sourceNodeId, targetNodeIds] of sourceTargets.entries()) {
    if (targetNodeIds.size > 1) {
      errors.push(
        `source node ${sourceNodeId} links to multiple targets: ${Array.from(targetNodeIds).join(", ")}`
      );
    }
  }

  return errors;
}
```

- [ ] **步骤 4：运行测试确认 `validate` 语义通过**

运行：

```powershell
node --test .agents\skills\code-trace-reading\scripts\trace-tools.test.js
```

预期：所有测试通过。

- [ ] **步骤 5：Commit**

```powershell
git add .agents/skills/code-trace-reading/scripts/trace-tools.js .agents/skills/code-trace-reading/scripts/trace-tools.test.js
git commit -m "fix(code-trace-reading): 收紧 trace link 校验规则"
```

## Task 3: 更新 skill 和 schema 文档，并完成端到端 smoke 验证

**文件：**
- 修改：`.agents/skills/code-trace-reading/SKILL.md`
- 修改：`.agents/skills/code-trace-reading/references/trace-schema.md`

- [ ] **步骤 1：先用文本检查确认文档仍停留在旧规则**

运行：

```powershell
rg -n "trace-<unix-seconds>|yyyyMMdd-主题.json|单行完整代码|每个 source 最多只能对应一个 target" .agents\skills\code-trace-reading\SKILL.md .agents\skills\code-trace-reading\references\trace-schema.md
```

预期：

- 仍能匹配到 `trace-<unix-seconds>.json`
- 还看不到 `yyyyMMdd-主题.json`
- 还看不到“单行完整代码”与“每个 source 最多只能对应一个 target”这两条规则

- [ ] **步骤 2：把 skill 和 schema 文档改成与脚本行为一致**

把 `.agents/skills/code-trace-reading/SKILL.md` 中的工作流和规则段落改成下面这些内容：

```md
1. Identify the artifact to update.
   - If the user points to an existing trace JSON, update that file in place unless they ask for a new one.
   - Otherwise create a new file under `code-trace/`, normally named `yyyyMMdd-主题.json`.
   - Derive `主题` from the current trace topic or feature name. Chinese is allowed. Remove only filesystem-illegal characters and obvious leading or trailing separators. If cleaning leaves an empty topic, fall back to `未命名主题`.
   - If the same-day file name already exists, append a numeric suffix such as `-2`.
2. Reconstruct the relevant implementation chain.
   - Prefer structural tools such as CodeGraph when available.
   - Otherwise use focused search plus file reads to find entry points, callees, persistence, framework callbacks, and output boundaries.
3. Select only meaningful nodes.
   - Include entry methods, important method calls, state mutations, branching decisions, persistence or network calls, framework hand-offs, and return points that explain the feature.
   - For every key cross-symbol call kept in the main chain, record a source node and a target node as a pair.
   - The source node must use the exact single-line call-site statement.
   - The target node must use the callee method or function declaration line.
   - Add a direct `source -> target` link between that pair.
   - A single source node may link to only one target node, although multiple source nodes may point to the same target node.
   - Skip trivial adjacent statements unless they are necessary to understand a data transformation or branch.
```

并把 `Existing Trace Updates` 与 `Trace Construction Rules` 两段补成：

```md
When editing an existing trace:

- Preserve `schemaVersion`, `id`, `createdAt`, existing node ids, and existing link ids unless the user asks to rebuild the file.
- Preserve existing notes that are still correct. Only rewrite when they are empty, shallow, or outdated.
- If the user adds nodes manually, explain only the new nodes unless the surrounding chain is now inconsistent.
- Keep links aligned with the actual execution or reasoning chain.
- Apply the paired source/target and single-source-single-target rules to links you add or modify in this turn. Do not stop only to rebuild untouched historical links.

Trace Construction Rules

- Follow the schema in `references/trace-schema.md`.
- Use repository-relative `filePath` values when the project stores nodes that way.
- Keep `line` aligned to the source line that best anchors the explanation.
- Keep `navigationHint` compatible with how the trace was captured. Reuse the existing style when updating a file.
- Use `MANUAL` links for hand-curated reasoning chains and `DETECTED` only when the relation was actually auto-detected by tooling.
- Every generated link must describe one concrete source/target node pair rather than a broad conceptual relationship.
```

把 `.agents/skills/code-trace-reading/references/trace-schema.md` 追加并改成下面这些段落：

```md
## File Naming For New Traces

- New traces should normally be stored as `yyyyMMdd-主题.json`.
- `主题` may contain Chinese text.
- Remove only filesystem-illegal characters and obvious leading or trailing separators.
- If cleaning leaves an empty topic, fall back to `未命名主题`.
- If the same-day file name already exists, append a numeric suffix such as `-2`.
- Older files such as `trace-*.json` remain valid historical artifacts and do not need to be renamed just to pass validation.
```

```md
### Link Guidance

- `sourceNodeId` and `targetNodeId` must reference existing nodes.
- Each link represents exactly one concrete source/target node pair.
- One source node may point to only one target node.
- Multiple source nodes may point to the same target node.
- Self-links are invalid.
- Use `MANUAL` for a human-curated reasoning chain.
- Use `DETECTED` only for relationships produced by detection logic.
```

```md
## Paired Call Example

For a kept cross-symbol call, record both the call-site statement and the callee declaration:

```json
{
  "nodes": [
    {
      "id": "node-source",
      "displayName": "TraceDocument document = storage.load(fileName);",
      "qualifiedName": "demo.StorageCaller#read",
      "signature": "TraceDocument document = storage.load(fileName);",
      "filePath": "src/main/java/demo/StorageCaller.java",
      "line": 42,
      "language": "JAVA",
      "note": "This line is the source node because it is the kept call-site statement in the main chain.",
      "navigationHint": "PsiStatement:42"
    },
    {
      "id": "node-target",
      "displayName": "public TraceDocument load(String fileName) {",
      "qualifiedName": "demo.Storage#load",
      "signature": "public TraceDocument load(String fileName) { ... }",
      "filePath": "src/main/java/demo/Storage.java",
      "line": 18,
      "language": "JAVA",
      "note": "This declaration is the target node reached by the kept call-site statement.",
      "navigationHint": "PsiMethod:load"
    }
  ],
  "links": [
    {
      "id": "link-source-target",
      "sourceNodeId": "node-source",
      "targetNodeId": "node-target",
      "createdAt": "2026-05-29T07:19:50.042448800Z",
      "kind": "MANUAL"
    }
  ]
}
```
```

- [ ] **步骤 3：重新运行自动化测试和文档文本检查**

运行：

```powershell
node --test .agents\skills\code-trace-reading\scripts\trace-tools.test.js
rg -n "trace-<unix-seconds>|yyyyMMdd-主题.json|单行完整代码|每个 source 最多只能对应一个 target" .agents\skills\code-trace-reading\SKILL.md .agents\skills\code-trace-reading\references\trace-schema.md
```

预期：

- `node --test` 全部通过
- `trace-<unix-seconds>.json` 不再出现在 `SKILL.md`
- `yyyyMMdd-主题.json`、`单行完整代码`、`每个 source 最多只能对应一个 target` 都能被匹配到

- [ ] **步骤 4：运行 CLI smoke check，验证默认命名和负例校验**

运行：

```powershell
$smokeRoot = Join-Path $PWD "build\trace-tools-smoke"
Remove-Item $smokeRoot -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $smokeRoot | Out-Null
$traceDir = Join-Path $smokeRoot "code-trace"

node .agents\skills\code-trace-reading\scripts\trace-tools.js new --name "登录/流程:保存?" --description "Smoke trace" --trace-dir $traceDir
$generated = Get-ChildItem $traceDir | Select-Object -ExpandProperty FullName
Write-Output $generated
node .agents\skills\code-trace-reading\scripts\trace-tools.js validate --input $generated

$invalidPath = Join-Path $smokeRoot "invalid.json"
@'
{
  "schemaVersion": 2,
  "id": "trace-1",
  "name": "Broken trace",
  "description": "",
  "createdAt": "2026-05-29T10:00:00Z",
  "updatedAt": "2026-05-29T10:00:00Z",
  "nodes": [
    {
      "id": "node-source",
      "displayName": "TraceDocument document = storage.load(fileName);",
      "qualifiedName": "demo.StorageCaller#read",
      "signature": "TraceDocument document = storage.load(fileName);",
      "filePath": "src/main/java/demo/StorageCaller.java",
      "line": 42,
      "language": "JAVA",
      "note": "",
      "navigationHint": "PsiStatement:42"
    },
    {
      "id": "node-target-a",
      "displayName": "public TraceDocument load(String fileName) {",
      "qualifiedName": "demo.Storage#load",
      "signature": "public TraceDocument load(String fileName) { ... }",
      "filePath": "src/main/java/demo/Storage.java",
      "line": 18,
      "language": "JAVA",
      "note": "",
      "navigationHint": "PsiMethod:load"
    },
    {
      "id": "node-target-b",
      "displayName": "public TraceDocument loadBackup(String fileName) {",
      "qualifiedName": "demo.Storage#loadBackup",
      "signature": "public TraceDocument loadBackup(String fileName) { ... }",
      "filePath": "src/main/java/demo/Storage.java",
      "line": 26,
      "language": "JAVA",
      "note": "",
      "navigationHint": "PsiMethod:loadBackup"
    }
  ],
  "links": [
    {
      "id": "link-1",
      "sourceNodeId": "node-source",
      "targetNodeId": "node-target-a",
      "createdAt": "2026-05-29T10:00:00Z",
      "kind": "MANUAL"
    },
    {
      "id": "link-2",
      "sourceNodeId": "node-source",
      "targetNodeId": "node-target-b",
      "createdAt": "2026-05-29T10:00:01Z",
      "kind": "MANUAL"
    }
  ]
}
'@ | Set-Content -Path $invalidPath -Encoding UTF8

node .agents\skills\code-trace-reading\scripts\trace-tools.js validate --input $invalidPath
```

预期：

- `new` 生成的文件路径以当天的 `yyyyMMdd-` 前缀开头，中文主题被保留，非法字符被替换掉
- 对新生成的空 trace 执行 `validate` 返回 `Valid trace: ...`
- 对 `invalid.json` 执行 `validate` 返回非零退出码，并包含 `source node node-source links to multiple targets`

- [ ] **步骤 5：Commit**

```powershell
git add .agents/skills/code-trace-reading/SKILL.md .agents/skills/code-trace-reading/references/trace-schema.md
git commit -m "docs(code-trace-reading): 对齐 trace 生成与链路规范"
```

## 自检

### 规格覆盖度

- 新建文件默认命名为 `yyyyMMdd-主题.json`：Task 1
- 主题允许中文并做最小清洗：Task 1、Task 3
- 重名追加后缀：Task 1
- 关键调用 source/target 成对记录：Task 3
- source 节点使用单行完整代码：Task 3
- target 节点使用声明行：Task 3
- 每个 source 最多一个 target：Task 2、Task 3
- target 可以被多个 source 指向：Task 2、Task 3
- 更新已有 trace 时只修本次新增或修改链路：Task 3
- 脚本能发现结构错误和 source 多 target 冲突：Task 2、Task 3

### 占位符扫描

- 本计划没有 `TODO`、`TBD`、`待定`、`后续实现` 之类占位词。
- 每个任务都给出了明确文件、命令、预期输出和代码块。

### 类型一致性

- 命令行新参数统一使用 `--trace-dir`
- 文件命名统一围绕 `yyyyMMdd-主题.json`
- 语义约束统一表述为 “一个 source 最多一个 target；多个 source 可以共享一个 target”
- 关键调用节点术语统一使用 `source node` 与 `target node`
