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
   - Otherwise create a new file under `code-trace/`, normally `trace-<unix-seconds>.json`.
2. Reconstruct the relevant implementation chain.
   - Prefer structural tools such as CodeGraph when available.
   - Otherwise use focused search plus file reads to find entry points, callees, persistence, framework callbacks, and output boundaries.
3. Select only meaningful nodes.
   - Include entry methods, important method calls, state mutations, branching decisions, persistence or network calls, framework hand-offs, and return points that explain the feature.
   - Skip trivial adjacent statements unless they are necessary to understand a data transformation or branch.
4. Write the trace summary.
   - Put the feature name in `name`.
   - Use `description` for a short walkthrough of the chain: where it starts, what core components participate, what state changes, and where the flow ends.
5. Write detailed node notes.
   - Explain what the code does in this exact location.
   - Explain why this node matters in the current chain.
   - Explain inputs, outputs, side effects, and any important assumptions.
   - Explain how execution moves to the next node or why the chain stops here.
6. Validate before finishing.
   - Keep the file schema compatible with the plugin.
   - Run `node scripts/trace-tools.js validate --input <trace-file>` from this skill directory when possible.

## Node Quality Bar

Every node `note` must be detailed enough that a developer can understand the code without reopening the file immediately. Prefer 3 to 6 sentences, or two short paragraphs when the logic is dense.

Cover these questions when they matter:

- What symbol or statement is this?
- What upstream context reaches this node?
- What data is read, transformed, or persisted here?
- What hidden coupling exists with framework state, services, or other nodes?
- Why is this node in the trace instead of being skipped?
- What node logically follows from here?

Avoid vague notes such as `calls service`, `saves data`, or `core logic`. Replace them with concrete behavior.

## Existing Trace Updates

When editing an existing trace:

- Preserve `schemaVersion`, `id`, `createdAt`, existing node ids, and existing link ids unless the user asks to rebuild the file.
- Preserve existing notes that are still correct. Only rewrite when they are empty, shallow, or outdated.
- If the user adds nodes manually, explain only the new nodes unless the surrounding chain is now inconsistent.
- Keep links aligned with the actual execution or reasoning chain. Do not add links between unrelated nodes just to make the graph denser.

## Trace Construction Rules

- Follow the schema in `references/trace-schema.md`.
- Use repository-relative `filePath` values when the project stores nodes that way.
- Keep `line` aligned to the source line that best anchors the explanation.
- Keep `navigationHint` compatible with how the trace was captured. Reuse the existing style when updating a file.
- Use `MANUAL` links for hand-curated reasoning chains and `DETECTED` only when the relation was actually auto-detected by tooling.

## Recommended File Strategy

- New feature question: create a fresh trace file and describe the full chain.
- Existing trace explanation request: enrich `description` and missing node `note` fields in the provided file.
- Multi-turn continuation: append or update only the nodes and links needed for the new request, then refresh the top-level description if the story changed.

## Resources

- Schema and field expectations: `references/trace-schema.md`
- Helper commands for creating and validating trace files: `scripts/trace-tools.js`
