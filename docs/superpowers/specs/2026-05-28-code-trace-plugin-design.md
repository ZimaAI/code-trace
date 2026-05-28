# Code Trace IntelliJ IDEA Plugin Design

## Overview

`code-trace` is an IntelliJ IDEA plugin that adds a dedicated Tool Window for recording and managing code navigation traces.

The plugin loads trace data from the project root `code-trace/` directory by default. Each JSON file represents one maintained trace document. A trace document contains:

- file-level metadata
- one editable current version
- multiple historical recorded versions
- per-node notes and a trace-level note

Users can:

- open the `code-trace` Tool Window from the IDE sidebar
- start and stop recording manually
- record only code navigation jumps while recording is active
- refresh files from disk
- create, rename, copy, delete, edit, and save trace files
- click any recorded node to navigate back to code

## Goals

- Provide a simple in-IDE workflow for recording method/function jump chains.
- Treat `code-trace/*.json` as the source of truth.
- Support both recorded traces and manual editing in the same UI.
- Preserve recording history without mixing it into the editable current version.
- Keep the first version language-agnostic as much as IntelliJ navigation APIs allow.

## Out Of Scope

- Automatic always-on recording.
- Recording arbitrary editor focus changes that are not caused by code navigation actions.
- Rich diffing between history versions.
- Guaranteed identical symbol resolution quality across all languages.

## User-Confirmed Product Decisions

- Recording is manual: users click `Start Recording` and `Stop Recording` in the Tool Window.
- The Tool Window provides a `Refresh` action to reload JSON files from disk.
- Users can manually create, update, and delete trace content.
- Notes exist at both levels: trace-level note and per-node note.
- A single JSON file contains one editable current version plus multiple history versions.
- Recorded nodes store stable navigation data: qualified symbol info, file path, and line.
- Recording captures only code navigation jumps, not every editor change.
- The design targets language-agnostic callable symbol capture where possible.
- Saving supports both auto-save and manual save, with dirty state tracking.
- Refresh with unsaved changes shows a confirmation dialog before discarding local edits.
- File management supports create, rename, copy, delete, and refresh.
- Consecutive duplicate nodes are controlled by a setting and are removed by default.

## Chosen UX Direction

The selected UI is a dual-pane Tool Window.

- Left pane: trace file list for `code-trace/*.json`
- Right pane: current file detail, recording controls, version content, history, and node editing

This layout was chosen because the plugin needs to combine file management, current-trace editing, recording control, history browsing, and code navigation inside one stable workspace.

## Architecture

The plugin is split into five focused units.

### 1. `CodeTraceToolWindowFactory`

Responsibilities:

- register the sidebar entry and Tool Window
- create the root UI container
- wire the main controller into the Tool Window

Non-responsibilities:

- no persistence logic
- no navigation event processing

### 2. `CodeTraceController` or `CodeTraceViewModel`

Responsibilities:

- own Tool Window state
- coordinate selected file, selected version, selected node, dirty state, and recording state
- process UI actions such as refresh, save, create, delete, copy, rename, start recording, and stop recording
- mediate conflict dialogs such as refresh-while-dirty

Non-responsibilities:

- no direct file IO
- no direct IntelliJ navigation event extraction

### 3. `TraceStorageService`

Responsibilities:

- ensure the project root `code-trace/` directory exists or is created on demand
- scan JSON files in that directory
- load, parse, validate, save, rename, copy, and delete trace documents
- manage schema versioning for stored JSON

Non-responsibilities:

- no UI state handling
- no code navigation tracking

### 4. `TraceRecordingService`

Responsibilities:

- start and stop recording sessions
- subscribe to IntelliJ code navigation actions while recording is active
- convert navigation targets into trace nodes
- apply consecutive duplicate suppression according to settings
- produce a new current version candidate on stop

Non-responsibilities:

- no disk persistence
- no Tool Window rendering

### 5. `CodeNavigationService`

Responsibilities:

- locate code from stored node data
- navigate the editor when a node is clicked
- apply fallback lookup strategies if exact location is stale
- report navigation failures clearly

Non-responsibilities:

- no trace editing
- no file management

## Tool Window Structure

### Left Pane: Trace File Manager

Displays all JSON files under `code-trace/`.

Actions:

- `New`
- `Rename`
- `Copy`
- `Delete`
- `Refresh`

Expected behavior:

- selecting a file loads its details in the right pane
- rename and copy update both disk state and in-memory selection
- delete removes the file and clears or re-targets the selection

### Right Pane: Trace Workspace

The right pane contains four functional zones.

#### A. Toolbar

Actions:

- `Start Recording`
- `Stop Recording`
- `Save`
- `Refresh`
- auto-save toggle
- consecutive-duplicate suppression toggle

State shown:

- recording or idle status
- dirty or saved status

#### B. Trace Metadata

Editable fields:

- trace name
- trace-level note or description

#### C. Current Version

Shows the editable current trace version:

- version source: manual or recording
- updated timestamp
- node list in order
- node-level note editor
- add, edit, delete, and reorder actions

#### D. History Versions

Shows prior versions as read-only snapshots by default.

Available actions:

- inspect a history version
- copy a history version into current
- copy a history version into a new file

History remains immutable unless explicitly promoted or copied.

## Data Model

Each file in `code-trace/` stores one trace document.

```json
{
  "schemaVersion": 1,
  "id": "trace-auth-login",
  "name": "Auth login flow",
  "description": "Trace-level note",
  "createdAt": "2026-05-28T12:00:00Z",
  "updatedAt": "2026-05-28T12:30:00Z",
  "current": {
    "versionId": "v3",
    "source": "recording",
    "recordedAt": "2026-05-28T12:30:00Z",
    "updatedAt": "2026-05-28T12:30:00Z",
    "nodeDedupEnabled": true,
    "nodes": []
  },
  "history": []
}
```

### Trace Document Fields

- `schemaVersion`: storage schema version for migration
- `id`: stable internal identifier
- `name`: display name shown in the Tool Window
- `description`: trace-level note
- `createdAt`: creation timestamp
- `updatedAt`: last document update timestamp
- `current`: editable active version
- `history`: previous snapshots

### Version Fields

- `versionId`
- `source`: `manual` or `recording`
- `recordedAt`
- `updatedAt`
- `nodeDedupEnabled`
- `nodes`

### Node Fields

- `id`
- `displayName`
- `qualifiedName`
- `signature`
- `filePath`
- `line`
- `language`
- `note`
- `navigationHint`

`navigationHint` stores extra lookup data needed for best-effort navigation across languages.

## Recording Rules

### Start Recording

When the user starts recording:

- the plugin switches state to `recording`
- navigation listeners are attached
- only code navigation jumps are eligible for capture

Examples of eligible inputs:

- go to declaration
- go to implementation
- go to super
- code navigation back or forward when they resolve to callable symbols

Non-goals:

- arbitrary file opens
- project tree selection
- search result clicks that do not resolve through the chosen navigation hook

### Stop Recording

When the user stops recording:

- navigation listeners are removed
- captured nodes are converted into a new version
- if a `current` version exists, it is first pushed into `history`
- the new recording becomes the new `current`
- the document becomes dirty unless auto-save writes it immediately

### Duplicate Handling

The plugin exposes a toggle for consecutive duplicate suppression.

- default: enabled
- enabled behavior: repeated consecutive jumps to the same node are collapsed
- disabled behavior: all captured consecutive repeats are preserved

Only consecutive duplicates are affected. Non-consecutive returns remain in the trace.

## Editing Rules

### Editable Areas

Users can manually edit:

- trace name
- trace-level note
- current-version nodes
- node notes
- node order

Users can manually add and delete nodes from the current version.

### History Rules

History versions are read-only by default.

To modify a historical trace, the user must:

- copy it into the current version, or
- copy it into a new file

This prevents accidental mutation of recording snapshots.

## Save And Refresh Behavior

### Save

The plugin supports both:

- manual save
- auto-save mode

Dirty state is tracked whenever the current document changes and has not yet been persisted.

### Refresh

Refresh reloads both:

- the file list under `code-trace/`
- the selected file content from disk

If the current document has unsaved changes:

- show a confirmation dialog
- allow `Discard local changes and refresh`
- allow `Cancel`

Refresh never silently overwrites unsaved local state.

## File Management Rules

Supported file-level actions:

- create
- rename
- copy to a new file
- delete
- refresh

File names are managed from the Tool Window rather than requiring external filesystem edits.

## Navigation From Nodes

When a user clicks a node:

1. try direct location using `filePath`, `line`, and `navigationHint`
2. if needed, fall back to symbol-based lookup using `qualifiedName` and `signature`
3. open the editor and focus the matched location

If navigation cannot be resolved:

- show a clear non-blocking message in the Tool Window
- indicate that code may have moved or been deleted

## Error Handling

The first version should handle these cases explicitly:

- missing `code-trace/` directory
- malformed JSON file
- unsupported or unknown schema version
- file conflicts during rename or copy
- failed save due to IO errors
- failed node navigation due to stale symbol data
- recording stopped with zero captured nodes

Expected behavior:

- do not crash the Tool Window
- keep messages actionable and local to the UI when possible

## Testing Strategy

### Unit Tests

Cover:

- JSON serialization and deserialization
- schema validation
- create, rename, copy, delete, and save behavior
- current-to-history promotion when a recording finishes
- dirty-state transitions
- duplicate suppression logic

### IntelliJ Platform Integration Tests

Cover:

- Tool Window creation
- default loading from project root `code-trace/`
- refresh flow and dirty-state confirmation
- save flow
- node click delegating to navigation services

### Manual Verification

Minimum scenarios:

- start recording, navigate across several callable symbols, stop recording
- record a second time and confirm old current moves into history
- edit trace note and node notes, save, and reload project
- modify JSON externally and confirm refresh reloads it
- click a valid node and navigate successfully
- click a stale node and see a clear failure message

## Risks And Constraints

### Language-Agnostic Capture

The plugin aims to support callable symbols across languages, but exact PSI or symbol resolution quality will vary by language and plugin ecosystem.

Design implication:

- storage and navigation must allow best-effort fallback
- the product should not promise identical fidelity for every language in the first version

### Navigation Hook Stability

IntelliJ navigation can be triggered through different actions and extension points. The implementation should choose a narrow, testable hook set that matches the agreed scope of "code navigation jumps" instead of attempting to observe every possible editor movement.

## Implementation Guidance

Keep the first implementation intentionally narrow:

- one Tool Window
- one storage directory convention
- one JSON schema version
- one current version plus history list
- one recording state machine with `idle` and `recording`

Avoid adding unrelated features such as cross-file linking, graph visualization, cloud sync, or diff viewers in the first cut.

## Acceptance Criteria

- The IDE shows a `code-trace` Tool Window entry.
- The Tool Window loads JSON files from the project root `code-trace/` directory.
- Users can start and stop recording manually.
- Only navigation jumps during recording are captured.
- Recorded output updates the current version and archives the previous current version into history.
- Users can edit trace-level and node-level notes.
- Users can manually add, modify, delete, and reorder current-version nodes.
- Users can create, rename, copy, delete, refresh, and save trace files from the Tool Window.
- Refresh prompts before discarding unsaved local changes.
- Clicking a node attempts to navigate to the corresponding code.
- Navigation failures surface a clear user-visible message.
