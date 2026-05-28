# Code-Trace 手工冒烟检查清单

## 启动

1. 在项目根目录执行：
   `.\gradlew.bat runIde`
2. 等待 Sandbox IDEA 启动。
3. 在 Sandbox IDEA 中打开任意含源码的项目。

## Tool Window 基础

1. 左侧工具栏可见 `code-trace`。
2. 打开后能看到：
   - 顶部按钮：`Start Recording / Stop Recording / Save / Refresh`
   - 左侧文件列表
   - 右侧编辑区（trace note、节点列表、node note、history）

## 文件管理

1. 新建：新增 `new-trace.json`（或自定义名）并自动加载。
2. 重命名：文件名变化后仍保持选中。
3. 复制：生成 `*-copy.json`。
4. 删除：确认后移除文件。
5. 刷新：外部改 JSON 后点击 `Refresh` 能重载。

## 录制流程

1. 点击 `Start Recording`。
2. 在编辑器执行 2-3 次方法跳转（如 `Ctrl+B`）。
3. 点击 `Stop Recording`。
4. 当前版本节点列表出现新节点；再次录制后历史列表新增一项。

## 节点编辑能力

1. `Add Node`：弹窗填写字段，节点加入列表。
2. `Edit Node`：修改 display name / line / note 后保存，列表更新。
3. `Delete Node`：删除选中节点。
4. `Move Up / Move Down`：顺序正确变化。
5. 选中节点修改 `node note`，点击 `Save` 后重启 Sandbox 再看，备注仍在。

## 历史版本视图

1. 在 history 列表点击 `Current` 和任意 `History`。
2. 节点列表能切换到对应版本内容。
3. 在 `History` 视图下：
   - `node note` 为只读
   - 节点增删改和排序按钮不可用

## 跳转验证

1. 双击当前节点：编辑器定位到目标文件与行。
2. 双击历史节点：同样可定位（若代码已变动可接受失败提示）。

## 自动化回归

执行：

`.\gradlew.bat test`

期望：`BUILD SUCCESSFUL`
