# Code Trace Single-Version Node Linking Design

## Overview

This design replaces the current recording-plus-history workflow with a single-version trace editor centered on manually captured nodes and explicit node links.

The upgraded plugin keeps JSON files under the project-root `code-trace/` directory, but each file now represents one current trace only. Nodes remain the primary editable unit. Links are stored separately and represent directional relationships between nodes.

The design responds to six product decisions confirmed during brainstorming:

- remove `Start Recording` and `Stop Recording`
- remove `History`
- remove `Add Node`
- store and display `displayName` as the raw single-line code text from the current editor line
- capture nodes from an editor context-menu action instead of a recording session
- support explicit node linking, unlinking, pair-aware move, and pair-aware delete

## Goals

- Eliminate note-edit lag by using explicit save buttons for trace notes and node notes.
- Show only `displayName` anywhere a node list is rendered.
- Replace recording sessions with an editor action that adds the current line into the active trace.
- Preserve source-to-target method relationships as explicit links between nodes.
- Allow users to create and remove links manually inside the Tool Window.
- Remove version history and keep exactly one persisted version per JSON file.

## Out Of Scope

- Continuous or background navigation recording.
- Multiple historical versions per trace file.
- Rich graph canvas editing.
- Automatic trace file creation from the editor context menu.
- Multi-target link suggestions for a single editor action.

## User-Confirmed Product Decisions

- Nodes remain the primary stored entity. Links are separate relationships between nodes.
- The editor context menu adds an independent node by default.
- `displayName` stores the raw text of the current editor line.
- There is no automatic recording after removing `Start Recording` and `Stop Recording`.
- After adding a node from the editor, the plugin always performs one best-effort candidate-target lookup and asks whether to add and link it only when a candidate is found.
- `trace note` and `node note` each have their own explicit save button.
- Saving a note writes directly to disk.
- Structural operations also write directly to disk.
- Linked nodes use a special visual style in the node list.
- Linked nodes do not need to be adjacent in the list.
- If a node is linked, move and delete operations act on the whole linked pair.
- A node can participate in at most one link.
- Manual linking is done through `Set as Source`, `Link To Here`, and `Unlink`.
- Any node presentation that shows trace nodes must display only `displayName`.

## UX Direction

The plugin stays a dual-pane Tool Window.

- Left pane: JSON file list under `code-trace/`
- Right pane: single-version trace editor with note editing, node list, node actions, and link actions

This preserves the current workspace model while simplifying the right side from recording-and-history management into a focused editor.

## Data Model

### Trace Document

`TraceDocument` becomes a single-version document.

```json
{
  "schemaVersion": 2,
  "id": "trace-auth-login",
  "name": "Auth login flow",
  "description": "Trace-level note",
  "createdAt": "2026-05-29T12:00:00Z",
  "updatedAt": "2026-05-29T12:30:00Z",
  "nodes": [],
  "links": []
}
```

Fields:

- `schemaVersion`
- `id`
- `name`
- `description`
- `createdAt`
- `updatedAt`
- `nodes`
- `links`

Removed fields:

- `current`
- `history`

### Trace Node

`TraceNode` keeps the existing navigation payload so node navigation remains intact.

Fields:

- `id`
- `displayName`
- `qualifiedName`
- `signature`
- `filePath`
- `line`
- `language`
- `note`
- `navigationHint`

Rules:

- `displayName` stores the raw text of the editor line, with line breaks removed.
- Any list or picker that renders trace nodes must use `displayName` only.
- `qualifiedName`, `signature`, and `navigationHint` remain best-effort metadata and can be empty if symbol resolution is unavailable.

### Trace Link

Add a new `TraceLink` model:

- `id`
- `sourceNodeId`
- `targetNodeId`
- `createdAt`
- `kind`

`kind` values:

- `MANUAL`
- `DETECTED`

Rules:

- A node can participate in at most one link total.
- Self-links are invalid.
- Duplicate links are invalid.
- Link direction is always `source -> target`.
- Linked nodes are not required to be adjacent in the list.

## Storage And Migration

### Storage Behavior

The JSON file on disk is the source of truth.

- Adding a node saves immediately.
- Editing a node saves immediately.
- Deleting a node saves immediately.
- Moving a node or linked pair saves immediately.
- Creating a link saves immediately.
- Removing a link saves immediately.
- Saving `trace note` writes immediately.
- Saving `node note` writes immediately.

The old file-level dirty/save workflow is removed as the primary interaction model.

### Migration Strategy

Schema version advances from `1` to `2`.

When reading a schema v1 file:

- load document metadata from the existing file
- import only `current.nodes` into `nodes`
- initialize `links` as an empty list
- discard `history`

When the migrated document is next written, it is persisted in schema v2 shape.

No attempt is made to reconstruct links from legacy recording history.

## Tool Window Structure

### Left Pane

The file list continues to show JSON file names from `code-trace/`.

Supported file actions remain:

- `New`
- `Rename`
- `Copy`
- `Delete`
- `Refresh`

### Right Pane

The right pane becomes a single-version editor containing:

- trace note editor
- `Save Trace Note` button
- node list
- node action toolbar
- node note editor
- `Save Node Note` button
- link-status area showing the currently staged source node for manual linking

Removed UI sections:

- recording toolbar actions
- history list
- history switching behavior
- file-level `Save` button
- `Add Node` button

## Node List Rendering

Each node row renders only `displayName`.

Linked nodes use special styling to surface directionality:

- source node styling
- target node styling
- a visible association cue showing that the selected nodes are linked

The row text content remains exactly `displayName`. Source and target distinction is expressed through non-text styling only, using distinct row decoration such as color, border, or icon treatment.

Double-clicking a node still triggers code navigation using the stored node metadata.

## Editing And Linking Behavior

### Note Editing

`trace note` and `node note` no longer save while typing.

Behavior:

- editing a note updates only the corresponding text area state
- the save button becomes enabled only when the text differs from the persisted value
- clicking the save button writes the note directly to disk
- on save failure, keep the edited text and leave the button enabled for retry

### Node Actions

Retained actions:

- `Edit Node`
- `Delete Node`
- `Move Up`
- `Move Down`

New actions:

- `Set as Source`
- `Link To Here`
- `Unlink`

Removed action:

- `Add Node`

### Manual Link Flow

1. Select a node and click `Set as Source`.
2. The UI stores that node as the pending source and displays its status in the link-status area.
3. Select another node and click `Link To Here`.
4. The controller validates the link rules and persists the link immediately.

`Unlink` removes the link for the selected linked node and persists immediately.

### Pair-Aware Move And Delete

If a selected node is linked:

- `Move Up` and `Move Down` act on both linked nodes as one logical operation
- `Delete` deletes both linked nodes and their link

Additional rules:

- linked nodes need not be adjacent before or after the operation
- if a requested pair move would push either node out of list bounds, cancel the whole move
- deleting a linked node always removes the entire pair

If a selected node is not linked, move and delete continue to operate on that single node.

## Editor Context Menu Integration

### New Action

Add a new editor context-menu action:

- `Add to code-trace`

The action is enabled only when:

- a project is open
- the `code-trace` Tool Window has a currently selected JSON document

If no trace document is selected, show a message telling the user to pick a JSON file in the Tool Window first.

### Adding A Node

When the action runs:

- read the current caret line text
- normalize it into a single line and store it as `displayName`
- collect `filePath`, `line`, and `language`
- attempt best-effort resolution for `qualifiedName`, `signature`, and `navigationHint`
- append the node to the active trace and persist immediately
- refresh the Tool Window view and select the new node when possible

The action adds an independent node by default and does not create a link automatically.

### Candidate Target Detection

After adding the source node, the plugin always attempts one best-effort target-method resolution based on the current PSI context.

Rules:

- detect at most one candidate target
- if no candidate is found, stop silently
- if a candidate is found, ask whether to add the target and create a link

If the user confirms:

- if the target node already exists, reuse it
- otherwise create a new target node using the target line text as `displayName`
- validate the link constraints
- create a `DETECTED` link when valid

If validation fails, keep the already saved source node and cancel only the link creation step.

## Error Handling

The plugin must handle these cases without crashing the Tool Window:

- no selected JSON document when the editor action runs
- failed symbol resolution for the optional target candidate
- link creation rejected because of self-link, duplicate link, or link-capacity conflict
- pair move cancelled because one side would leave list bounds
- disk write failure during any immediate-save action
- node navigation target becoming stale

Expected behavior:

- keep the UI responsive
- keep already-persisted data intact
- show local, actionable feedback

## Testing Strategy

### Unit Tests

Cover:

- schema v1 to v2 migration
- schema v2 serialization and deserialization
- immediate-save node add, edit, delete, move, link, and unlink flows
- note save button enablement and persistence behavior
- one-link-per-node validation
- pair-aware move and pair-aware delete behavior

### UI And Controller Tests

Cover:

- removed controls are no longer rendered
- node list rows display only `displayName`
- link actions stage and clear pending source state correctly
- linked nodes use distinct source and target styling
- context-menu add updates the current Tool Window state

### Integration Tests

Cover:

- editor context-menu `Add to code-trace`
- detected-target confirmation flow
- navigation still works when a node is double-clicked
- refresh reloads a v2 file correctly

## Risks And Constraints

### PSI Resolution Quality

Candidate target detection remains best-effort and language-plugin dependent.

Implication:

- the plugin must treat detection as optional assistance, not as a guaranteed workflow

### Pair Moves With Non-Adjacent Nodes

Because linked nodes are not required to be adjacent, pair-aware move logic must define movement in terms of individual list indices rather than contiguous ranges.

Implication:

- implementation should centralize pair move calculations in one controller method and test it heavily

### Immediate Persistence

Immediate persistence simplifies the UX but increases the frequency of disk writes.

Implication:

- storage writes must stay small and error handling must be explicit

## Acceptance Criteria

- The Tool Window no longer shows `Start Recording`, `Stop Recording`, `History`, `Add Node`, or file-level `Save`.
- Each trace JSON file stores exactly one current document in schema v2 shape.
- Node lists display only `displayName`.
- `displayName` is captured from the raw current editor line text.
- `trace note` and `node note` each save through their own explicit button.
- Structural node and link actions persist immediately.
- The editor context menu offers `Add to code-trace`.
- The default context-menu action adds one independent node to the selected trace.
- The plugin can optionally suggest and create one detected source-to-target link after that action.
- Users can manually create a link using `Set as Source` and `Link To Here`.
- Users can remove a link using `Unlink`.
- A linked node participates in move and delete as a whole pair.
- A node can participate in at most one link.
- Double-clicking a node still attempts code navigation.
