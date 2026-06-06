---
name: code-trace-reading
description: Generate and extend code-trace JSON files with detailed code explanations for unfamiliar projects. Use when a user asks how a feature is implemented and needs a trace chain written to code-trace/*.json, when a user provides an existing code-trace JSON and wants node-by-node explanations or note enrichment, or when a multi-turn discussion continues from an existing trace and newly added nodes need detailed notes.
---

# Code Trace Reading

## Overview

Read only the code needed to explain the requested feature, then write or update a `code-trace` trace file that the IDEA plugin can open directly.

Prioritize a trace that helps a developer understand control flow, state changes, persistence, and hand-offs between components. Fill the trace `description` and every node `note` with concrete explanations rather than short labels.

## Workflow

1. Identify the artifact to update.
   - If the user points to an existing trace JSON, update that file in place unless they ask for a new one.
   - Otherwise create a new file under `code-trace/`, normally named `yyyyMMdd-主题.json`.
   - Derive `主题` from the current trace topic or feature name. Chinese is allowed. Remove only filesystem-illegal characters and obvious leading or trailing separators.
   - If cleaning leaves an empty topic, fall back to `未命名主题`.
   - If the same-day file name already exists, append a numeric suffix such as `-2`.
2. Reconstruct the relevant implementation chain.
   - Prefer structural tools such as CodeGraph when available.
   - Otherwise use focused search plus file reads to find entry points, callees, persistence, framework callbacks, and output boundaries.
   - **Trace depth strategy**: Start from the entry point, follow the primary execution path through at least 3-5 method calls deep. For complex features, trace through multiple layers (controller → service → repository → database). Don't stop at the first callee—continue until you reach a meaningful boundary (persistence, network, UI update, or return to caller).
   - **Cross-component tracing**: When the flow crosses component boundaries (e.g., controller to service, service to repository), always include both the call-site and the callee declaration as paired nodes.
   - **State mutation tracking**: If a method modifies state (fields, collections, files, database), include the mutation point and explain what changed.
3. Select only meaningful nodes.
   - Include entry methods, important method calls, state mutations, branching decisions, persistence or network calls, framework hand-offs, and return points that explain the feature.
   - For every key cross-symbol call kept in the main chain, record a source node and a target node as a pair.
   - The source node must use the exact single-line call-site statement.
   - The target node must use the callee method or function declaration line.
   - Add a direct `source -> target` link between that pair.
   - A single source node may link to only one target node, although multiple source nodes may point to the same target node.
   - Skip trivial adjacent statements unless they are necessary to understand a data transformation or branch.
4. Write the trace summary.
   - Put the feature name in `name`.
   - Use `description` for a short walkthrough of the chain: where it starts, what core components participate, what state changes, and where the flow ends.
   - **Description quality**: Write 2-4 sentences that tell the complete story. Mention the entry point, key collaborators, the main business effect, and the end state. A developer should understand the entire flow just from reading the description.
5. Write detailed node notes.
   - **Be concrete, not generic**: Instead of "calls service", write "forwards the user's note text and selected node id to TraceDocumentEditor.saveNodeNote, which rebuilds the node list immutably".
   - **Explain the "why"**: Don't just describe what the code does—explain why this step matters in the current chain. What would break if this node were skipped?
   - **Data flow**: Trace what data enters this node, how it's transformed, and what leaves. Mention specific field names, types, and transformations.
   - **Side effects**: Call out any state mutations, file I/O, network calls, or framework interactions.
   - **Transition logic**: Explain how execution moves to the next node. Is it a direct call? A callback? An event? A conditional branch?
   - **Context inheritance**: Reference what upstream nodes established. "Building on the validation in node-X, this method now persists the validated document."
6. Validate before finishing.
   - Keep the file schema compatible with the plugin (currently schemaVersion 3).
   - Run `node scripts/trace-tools.js validate --input <trace-file>` from this skill directory when possible.

## Node Quality Bar

Every node `note` must be detailed enough that a developer can understand the code without reopening the file immediately. **Minimum 3 sentences; aim for 4-6 sentences for non-trivial nodes.**

### Note Structure Template

For each node, structure the note to answer these questions in order:

1. **Responsibility**: What is this node's job in the chain? (1 sentence)
2. **Data handling**: What data enters, how is it transformed, what exits? (1-2 sentences)
3. **Why it matters**: Why is this node in the trace? What depends on it? (1 sentence)
4. **Transition**: How does execution proceed to the next node? (1 sentence)

### Example: Weak Note

```
Saves the note to storage.
```

### Example: Strong Note

```
This controller method orchestrates the note-save operation by coordinating three concerns: validation, mutation, and persistence. It first checks that the provided nodeId exists in the current document, then delegates the actual text replacement to TraceDocumentEditor.saveNodeNote, passing the document, nodeId, new note text, and current timestamp. The editor returns a new immutable TraceDocument with the updated node, which this method then forwards to TraceStorageService.save for disk persistence. This node matters because it's the single point where UI intent becomes a持久化 change—without it, edited notes would be lost on reload. After persistence succeeds, the flow continues to reload the document into controller state so the UI reflects the saved content.
```

### Density Guidelines

- **Simple getter/setter**: 2-3 sentences
- **Single-purpose method**: 3-4 sentences
- **Orchestrator/controller method**: 4-6 sentences
- **Complex logic with branches**: 5-7 sentences, explain each branch
- **State mutation point**: Always explain what changed and why it matters

## Trace Chain Quality

### Depth Requirements

- **Minimum depth**: 3 nodes for any non-trivial feature
- **Recommended depth**: 5-8 nodes for typical features
- **Complex features**: 10+ nodes, organized with clear layer transitions

### Chain Completeness

A complete trace should answer:

1. **Where does it start?** (entry point, user action, framework trigger)
2. **What validates/transforms the input?** (validation, sanitization, parsing)
3. **What performs the core logic?** (business rules, calculations, state changes)
4. **What persists the result?** (database, file, network, UI state)
5. **What confirms completion?** (return value, callback, UI update, event)

### Layer Transition Markers

When the trace crosses architectural layers, make the transition explicit in notes:

- **Controller → Service**: "This is the hand-off from HTTP/UI orchestration to business logic..."
- **Service → Repository**: "The service now delegates persistence to the repository layer..."
- **Repository → Database**: "This translates the domain operation into a SQL/datastore call..."
- **Async boundaries**: "Execution now crosses an async boundary via CompletableFuture/EventBus..."

## Existing Trace Updates

When editing an existing trace:

- Preserve `schemaVersion`, `id`, `createdAt`, existing node ids, and existing link ids unless the user asks to rebuild the file.
- Preserve existing notes that are still correct. Only rewrite when they are empty, shallow, or outdated.
- If the user adds nodes manually, explain only the new nodes unless the surrounding chain is now inconsistent.
- Keep links aligned with the actual execution or reasoning chain.
- Apply the paired source/target and single-source-single-target rules to links you add or modify in this turn. Do not stop only to rebuild untouched historical links.

## Trace Construction Rules

- Follow the schema in `references/trace-schema.md`.
- Use repository-relative `filePath` values when the project stores nodes that way.
- Keep `line` aligned to the source line that best anchors the explanation.
- Keep `navigationHint` compatible with how the trace was captured. Reuse the existing style when updating a file.
- Use `MANUAL` links for hand-curated reasoning chains and `DETECTED` only when the relation was actually auto-detected by tooling.
- Every generated link must describe one concrete source/target node pair rather than a broad conceptual relationship.

## Schema Version

Current schema version is **3**. Key fields:

- `schemaVersion`: Must be `3`
- `expandedNodeIds`: Optional array of node ids to expand in UI tree view (can be empty or omitted)
- `parentId`: Optional node field for tree structure (null for root nodes)
- `title`: Optional display title override (null to use displayName)

## Recommended File Strategy

- New feature question: create a fresh trace file and describe the full chain.
- Existing trace explanation request: enrich `description` and missing node `note` fields in the provided file.
- Multi-turn continuation: append or update only the nodes and links needed for the new request, then refresh the top-level description if the story changed.

## Resources

- Schema and field expectations: `references/trace-schema.md`
- Helper commands for creating and validating trace files: `scripts/trace-tools.js`
