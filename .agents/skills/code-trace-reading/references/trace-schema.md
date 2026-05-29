# Code Trace Schema

Use this reference when creating or editing trace files for the `code-trace` IDEA plugin in this repository.

## Storage Location

- Trace files live under the project-local `code-trace/` directory.
- The plugin resolves that directory from the project root in `TraceProjectPaths.traceDirectory()`.

## File Naming For New Traces

- New traces should normally be stored as `yyyyMMdd-主题.json`.
- `主题` may contain Chinese text.
- Remove only filesystem-illegal characters and obvious leading or trailing separators.
- If cleaning leaves an empty topic, fall back to `未命名主题`.
- If the same-day file name already exists, append a numeric suffix such as `-2`.
- Older files such as `trace-*.json` remain valid historical artifacts and do not need to be renamed just to pass validation.

## Top-Level Document Shape

```json
{
  "schemaVersion": 2,
  "id": "trace-<uuid>",
  "name": "Feature or flow name",
  "description": "High-value summary of the traced behavior",
  "createdAt": "2026-05-29T07:14:30.923554500Z",
  "updatedAt": "2026-05-29T07:48:02.981368100Z",
  "nodes": [],
  "links": []
}
```

## Field Meanings

- `schemaVersion`: Must be `2` for current writes.
- `id`: Stable trace id, normally prefixed with `trace-`.
- `name`: Short human-readable title for the feature or scenario.
- `description`: The narrative summary of the trace. Explain the chain, not just the topic.
- `createdAt`: Original creation time in ISO-8601 UTC.
- `updatedAt`: Last update time in ISO-8601 UTC.
- `nodes`: Ordered list of trace nodes.
- `links`: Directed relationships between nodes.

## Trace Node Shape

```json
{
  "id": "node-<uuid>",
  "displayName": "public void saveNodeNote(String nodeId, String note) {",
  "qualifiedName": "PsiMethod:saveNodeNote",
  "signature": "public void saveNodeNote(String nodeId, String note) { ... }",
  "filePath": "src/main/java/com/zimaai/codetrace/toolwindow/CodeTraceController.java",
  "line": 49,
  "language": "JAVA",
  "note": "Detailed explanation for developers.",
  "navigationHint": "PsiMethod:saveNodeNote"
}
```

### Node Field Guidance

- `id`: Stable node id, normally prefixed with `node-`.
- `displayName`: The exact line or short source snippet shown in the UI.
- `qualifiedName`: Symbol-oriented identifier. Reuse the plugin's capture style when available.
- `signature`: A larger source snippet or symbol signature that gives more context.
- `filePath`: Repository-relative path when possible.
- `line`: Source line number used for navigation.
- `language`: Uppercase language name such as `JAVA`.
- `note`: The most important explanatory field. Write the code-reading explanation here.
- `navigationHint`: Plugin-facing navigation hint. Preserve existing values when updating traces.

## Link Shape

```json
{
  "id": "link-<uuid>",
  "sourceNodeId": "node-a",
  "targetNodeId": "node-b",
  "createdAt": "2026-05-29T07:19:50.042448800Z",
  "kind": "MANUAL"
}
```

### Link Guidance

- `sourceNodeId` and `targetNodeId` must reference existing nodes.
- Each link represents exactly one concrete source/target node pair.
- One source node may point to only one target node.
- Multiple source nodes may point to the same target node.
- Self-links are invalid.
- Use `MANUAL` for a human-curated reasoning chain.
- Use `DETECTED` only for relationships produced by detection logic.

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

## Description Standard

Write `description` as a compact feature walkthrough:

1. Name the entry point or user action.
2. Identify the major collaborators.
3. Explain the main state change or business effect.
4. Mention the end state, persistence point, or UI update.

Bad:

`Handles saving notes.`

Good:

`This trace starts in CodeTraceController.saveNodeNote when the tool window saves an edited node note. The controller delegates the immutable document rewrite to TraceDocumentEditor.saveNodeNote, then persists the updated TraceDocument through TraceStorageService. The flow ends by reloading the saved file back into controller state so the UI reflects the latest note content.`

## Node Note Standard

Each `note` should make sense on its own. A strong note usually includes:

- The responsibility of the symbol or statement.
- The specific data being passed or rewritten.
- Why this step matters to the user-visible behavior.
- The transition to the next node in the chain.

Bad:

`Calls save logic.`

Good:

`This controller call is the hand-off from UI orchestration to document mutation. It forwards the currently loaded TraceDocument, the selected node id, the new note text, and the current timestamp into TraceDocumentEditor.saveNodeNote so the editor can rebuild the node list immutably. In this trace it matters because the actual note replacement does not happen in the controller; the controller only coordinates mutation and later persistence.`

## Update Rules

- Preserve old ids and timestamps unless the file is intentionally rebuilt.
- Keep notes specific to the current trace question. The same method can need different emphasis in different traces.
- If a node was manually inserted by the user, explain it with the same quality bar instead of replacing it with a different node.
