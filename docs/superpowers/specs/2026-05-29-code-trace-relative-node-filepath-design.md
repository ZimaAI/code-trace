# Code Trace Relative Node File Path Design

## Overview

This design changes node `filePath` storage so newly captured nodes store a path relative to the project root by default.

Existing trace files that already contain absolute paths remain readable and navigable. The plugin does not migrate old JSON on load and does not introduce a new schema version for this change.

The change is intentionally narrow. It only adjusts how captured nodes derive `filePath`, how navigation resolves `filePath`, and how the editor action behaves when the selected file is outside the current project root.

## Goals

- Store project-internal node paths relative to the project root by default.
- Preserve navigation compatibility for existing nodes that already store absolute paths.
- Reject new node capture for files outside the current project root.
- Keep the JSON schema and `TraceNode` shape unchanged.

## Out Of Scope

- Migrating existing JSON files from absolute paths to relative paths.
- Adding validation for manually edited node `filePath` values.
- Rewriting loaded absolute paths before the user saves.
- Changing node deduplication rules beyond using whatever `filePath` value is already stored on the node.

## User-Confirmed Product Decisions

- New captured nodes should store `filePath` relative to the project root by default.
- Existing JSON that stores absolute paths must still load and navigate correctly.
- Files outside the project root must not be added through the editor capture action.
- Manual node editing does not gain new `filePath` validation in this change.
- If a node already stores an absolute path, navigation may still open it as long as the path exists on disk.

## Current Touchpoints

The current code stores and consumes `filePath` in three places:

- `PsiTraceNodeCaptureService` builds new `TraceNode` instances from editor context and currently writes `VirtualFile.getPath()`.
- `CodeNavigationService` currently assumes `node.filePath()` is already a directly openable filesystem path.
- `AddToCodeTraceHandler` owns the editor action flow and is the right place to stop capture when a file is not allowed.

`TraceJsonMapper` should remain unaware of project-root path semantics. It should continue to read and write node data without path conversion logic.

## Design

### Path Storage Rules

Newly captured nodes use these storage rules:

- If the captured file is inside the current project root, store `filePath` as a relative path from the project root.
- Normalize the stored relative path to `/` separators so JSON remains stable across platforms.
- If the node was not created by the capture flow, keep the supplied `filePath` value unchanged.

This keeps storage policy limited to the capture boundary instead of silently rewriting existing data elsewhere.

### Capture Flow

`PsiTraceNodeCaptureService` must determine whether the current file belongs to the active project root before creating a persisted node payload.

Expected behavior:

1. Resolve the project root path.
2. Resolve the selected file path.
3. If the file is under the project root, compute the relative path and build the node normally.
4. If the file is outside the project root, do not produce a capturable node result.

`AddToCodeTraceHandler` then treats that result as a rejected capture:

- stop the add flow immediately
- show a direct error message explaining that only project-root files are supported
- skip target detection and link creation
- leave the current trace document unchanged

This restriction applies only to new editor-driven capture. It does not retroactively invalidate stored nodes.

### Navigation Resolution Rules

`CodeNavigationService` must support both stored path formats:

- If `filePath` is absolute, try to open it directly.
- If `filePath` is relative, resolve it against the project root and then try to open the resulting absolute path.

If the final resolved file does not exist, navigation fails in the same way it does today.

This preserves backward compatibility while allowing new relative-path nodes to navigate without schema changes.

### External File Behavior

The plugin distinguishes between new capture and existing data:

- New capture from a project-external file is rejected.
- Existing nodes that already contain an absolute path are still allowed to navigate.
- Manually edited nodes are still saved as entered, because manual `filePath` validation is out of scope for this change.

This boundary matches the confirmed product decision: default storage should favor project-relative paths, but compatibility with old absolute-path records must remain intact.

### Data Model And Persistence Boundaries

No data model changes are required:

- `TraceNode.filePath` remains a plain `String`
- `TraceDocument.schemaVersion` stays unchanged
- `TraceJsonMapper` keeps raw read/write responsibility only

This avoids inventing a migration for a behavior change that can be handled at the capture and navigation edges.

## Error Handling

- Missing project base path should continue to surface as a storage/navigation level failure rather than producing a fake relative path.
- Capture rejection for project-external files should show a user-facing message and return without mutating the active trace.
- Navigation should return failure for blank paths, unresolved relative paths, and missing files.

## Testing

### Unit Tests

- `CodeNavigationService`
  - resolves project-relative `filePath` values against the project root
  - still opens absolute paths directly
  - returns failure for blank or missing paths
- `PsiTraceNodeCaptureService`
  - stores a project-internal file as a normalized relative path
  - rejects files outside the project root

### Behavior Tests

- `AddToCodeTraceHandler`
  - adds a project-internal node successfully
  - stops the flow and shows an error for a project-external file
  - does not add nodes or links after external-file rejection

### Regression Checks

- Existing mapper tests keep proving that no schema migration is required for this change.
- Add a persistence assertion that absolute `filePath` values remain unchanged when read from JSON.
- Manually edited relative and absolute `filePath` values continue to rely on navigation-time resolution only.

## Tradeoffs

### Why Not Convert Paths In The JSON Mapper

The mapper does not know the active IntelliJ project root and should not take on environment-dependent path logic. Putting conversion there would couple persistence to IDE runtime context and make tests less direct.

### Why Not Normalize Every Save

Auto-rewriting all node paths on every save would expand scope beyond the confirmed requirement. It would also alter hand-edited values and old files unexpectedly. The design keeps normalization only at the capture boundary.

## Acceptance Criteria

- Capturing a node from a file inside the project root stores a project-relative `filePath`.
- Capturing a node from a file outside the project root does not modify the trace and shows a rejection message.
- Opening a node with a relative `filePath` navigates to the correct file under the current project root.
- Opening a node with an existing absolute `filePath` still works.
- No schema version or JSON structure change is introduced.
