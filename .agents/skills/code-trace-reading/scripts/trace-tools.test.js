const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { spawnSync } = require("node:child_process");

const scriptPath = path.resolve(__dirname, "trace-tools.js");
const fixedIso = "2026-05-09T12:00:00.000Z";
const fixedDate = "20260509";

function createTempDir() {
  return fs.mkdtempSync(path.join(os.tmpdir(), "trace-tools-test-"));
}

function writeDateMock(tempDir) {
  const preloadPath = path.join(tempDir, "mock-date.cjs");
  const content = `
const fixed = new Date(${JSON.stringify(fixedIso)});
class FixedDate extends Date {
  constructor(...args) {
    if (args.length === 0) {
      super(fixed.getTime());
      return;
    }
    super(...args);
  }
  static now() {
    return fixed.getTime();
  }
}
global.Date = FixedDate;
`;
  fs.writeFileSync(preloadPath, content, "utf8");
  return preloadPath;
}

function runNewCommand({ cwd, name, traceDir }) {
  const preloadPath = writeDateMock(cwd);
  const args = ["-r", preloadPath, scriptPath, "new", "--name", name];
  if (traceDir) {
    args.push("--trace-dir", traceDir);
  }
  return spawnSync(process.execPath, args, { cwd, encoding: "utf8" });
}

function createdPathFrom(result) {
  const line = result.stdout
    .split(/\r?\n/)
    .find((entry) => entry.startsWith("Created "));
  assert.ok(line, `expected creation output, got stdout="${result.stdout}" stderr="${result.stderr}"`);
  return line.slice("Created ".length).trim();
}

test("new 命令在未传 output 时按 yyyyMMdd-主题 命名", () => {
  const tempDir = createTempDir();
  const traceDir = path.join(tempDir, "trace");

  const result = runNewCommand({ cwd: tempDir, name: "default-topic", traceDir });

  assert.equal(result.status, 0, result.stderr);
  const createdPath = createdPathFrom(result);
  const expectedPath = path.resolve(traceDir, `${fixedDate}-default-topic.json`);
  assert.equal(createdPath, expectedPath);
  assert.ok(fs.existsSync(expectedPath));
});

test("主题保留中文并清洗非法文件名字符", () => {
  const tempDir = createTempDir();
  const traceDir = path.join(tempDir, "trace");
  const name = '主题:一/二*三?四<五>六|"七\\八';

  const result = runNewCommand({ cwd: tempDir, name, traceDir });

  assert.equal(result.status, 0, result.stderr);
  const createdPath = createdPathFrom(result);
  const expectedPath = path.resolve(traceDir, `${fixedDate}-主题一二三四五六七八.json`);
  assert.equal(createdPath, expectedPath);
  assert.ok(fs.existsSync(expectedPath));
});

test("主题清洗后为空时回退到 未命名主题", () => {
  const tempDir = createTempDir();
  const traceDir = path.join(tempDir, "trace");
  const name = '<>:"/\\|?*';

  const result = runNewCommand({ cwd: tempDir, name, traceDir });

  assert.equal(result.status, 0, result.stderr);
  const createdPath = createdPathFrom(result);
  const expectedPath = path.resolve(traceDir, `${fixedDate}-未命名主题.json`);
  assert.equal(createdPath, expectedPath);
  assert.ok(fs.existsSync(expectedPath));
});

test("同日同主题重名时追加 -2 和 -3", () => {
  const tempDir = createTempDir();
  const traceDir = path.join(tempDir, "trace");
  const name = "dup-topic";

  const first = runNewCommand({ cwd: tempDir, name, traceDir });
  const second = runNewCommand({ cwd: tempDir, name, traceDir });
  const third = runNewCommand({ cwd: tempDir, name, traceDir });

  assert.equal(first.status, 0, first.stderr);
  assert.equal(second.status, 0, second.stderr);
  assert.equal(third.status, 0, third.stderr);

  assert.equal(createdPathFrom(first), path.resolve(traceDir, `${fixedDate}-dup-topic.json`));
  assert.equal(createdPathFrom(second), path.resolve(traceDir, `${fixedDate}-dup-topic-2.json`));
  assert.equal(createdPathFrom(third), path.resolve(traceDir, `${fixedDate}-dup-topic-3.json`));
});
