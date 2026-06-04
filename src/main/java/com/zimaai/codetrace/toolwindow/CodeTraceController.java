package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.storage.TraceStorageService;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public final class CodeTraceController {
    private final TraceStorageService storage;
    private final Function<TraceNode, Boolean> navigationHandler;
    private final TraceDocumentEditor editor = new TraceDocumentEditor();
    private final CodeTraceState state = new CodeTraceState();

    public CodeTraceController(TraceStorageService storage, Function<TraceNode, Boolean> navigationHandler) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.navigationHandler = Objects.requireNonNull(navigationHandler, "navigationHandler");
    }

    public List<String> loadFileNames() {
        return storage.listFiles();
    }

    public TraceDocument load(String fileName) {
        TraceDocument document = storage.load(fileName);
        state.load(fileName, document);
        return document;
    }

    public void refreshCurrentFile() {
        if (state.currentFileName() != null) {
            state.load(state.currentFileName(), storage.load(state.currentFileName()));
        }
    }

    public void saveDescription(String description) {
        TraceDocument updated = editor.saveDescription(requireDocument(), description, Instant.now());
        persist(updated);
    }

    public void saveNodeNote(String nodeId, String note) {
        TraceDocument updated = editor.saveNodeNote(requireDocument(), nodeId, note, Instant.now());
        persist(updated);
    }

    public int addNode(TraceNode node) {
        TraceDocument updated = editor.insertNodeAt(requireDocument(), node, requireDocument().nodes().size(), Instant.now());
        persist(updated);
        return updated.nodes().size() - 1;
    }

    public int addOrReuseNode(TraceNode candidate) {
        List<TraceNode> nodes = requireDocument().nodes();
        for (int i = 0; i < nodes.size(); i++) {
            TraceNode existing = nodes.get(i);
            if (existing.displayName().equals(candidate.displayName())
                    && existing.filePath().equals(candidate.filePath())
                    && existing.line() == candidate.line()) {
                return i;
            }
        }
        TraceNode withId = new TraceNode(
                "node-" + UUID.randomUUID(),
                candidate.displayName(),
                candidate.qualifiedName(),
                candidate.signature(),
                candidate.filePath(),
                candidate.line(),
                candidate.language(),
                candidate.note(),
                candidate.navigationHint());
        return addNode(withId);
    }

    public void setFocusedNodeId(String nodeId) {
        state.setFocusedNodeId(nodeId);
    }

    public void clearFocusedNodeId() {
        state.clearFocusedNodeId();
    }

    public String focusedNodeId() {
        return state.focusedNodeId();
    }

    public int addOrReuseNodeAfterFocusedNode(TraceNode candidate) {
        String afterNodeId = state.focusedNodeId();
        TraceDocument updated = insertOrReuseNodeAfter(requireDocument(), candidate, afterNodeId, Instant.now());
        persist(updated);
        return indexOfNode(updated.nodes(), resolveInsertedNodeId(updated, candidate));
    }

    public void moveNodeToIndex(String nodeId, int targetIndex) {
        TraceDocument updated = moveInternalToIndex(requireDocument(), nodeId, targetIndex, Instant.now());
        persist(updated);
    }

    public void updateNode(TraceNode node) {
        TraceDocument updated = editor.updateNode(requireDocument(), node, Instant.now());
        persist(updated);
    }

    public void setPendingLinkSource(String nodeId) {
        state.setPendingLinkSourceId(nodeId);
    }

    public void linkPendingSourceTo(String targetNodeId, TraceLinkKind kind) {
        String sourceNodeId = Objects.requireNonNull(state.pendingLinkSourceId(), "pendingLinkSourceId");
        TraceDocument updated = editor.link(requireDocument(), sourceNodeId, targetNodeId, kind, Instant.now());
        persist(updated);
        state.clearPendingLinkSource();
    }

    public void unlinkNode(String nodeId) {
        TraceDocument updated = editor.unlink(requireDocument(), nodeId, Instant.now());
        persist(updated);
        if (nodeId.equals(state.pendingLinkSourceId())) {
            state.clearPendingLinkSource();
        }
    }

    public int moveNode(String nodeId, int offset) {
        TraceDocument updated = moveInternal(requireDocument(), nodeId, offset, Instant.now());
        persist(updated);
        return indexOfNode(updated.nodes(), nodeId);
    }

    public void deleteNode(String nodeId) {
        TraceDocument updated = deleteInternal(requireDocument(), nodeId, Instant.now());
        persist(updated);
        if (nodeId.equals(state.pendingLinkSourceId())) {
            state.clearPendingLinkSource();
        }
    }

    public boolean navigateToNode(TraceNode node) {
        return navigationHandler.apply(node);
    }

    public void renameCurrentFile(String newFileName) {
        if (state.currentFileName() == null) {
            return;
        }
        storage.rename(state.currentFileName(), newFileName);
        state.load(newFileName, state.currentDocument());
    }

    public void copyCurrentFile(String newFileName) {
        if (state.currentFileName() == null) {
            return;
        }
        storage.copy(state.currentFileName(), newFileName);
    }

    public void deleteCurrentFile() {
        if (state.currentFileName() == null) {
            return;
        }
        storage.delete(state.currentFileName());
        state.load(null, null);
    }

    public TraceDocument createNewFile(String fileName, String displayName) {
        TraceDocument document = createEmptyDocument(displayName);
        storage.save(fileName, document);
        state.load(fileName, document);
        return document;
    }

    public TraceDocument ensureAnyFileLoaded() {
        List<String> files = storage.listFiles();
        if (!files.isEmpty()) {
            return load(files.get(0));
        }
        String fileName = "trace-" + Instant.now().atZone(ZoneOffset.UTC).toEpochSecond() + ".json";
        return createNewFile(fileName, "New Trace");
    }

    public CodeTraceState state() {
        return state;
    }

    public void preferSelectedNode(String nodeId) {
        state.setPreferredSelectedNodeId(nodeId);
    }

    public String consumePreferredSelectedNodeId() {
        return state.consumePreferredSelectedNodeId();
    }

    public void setParent(String nodeId, String newParentId) {
        TraceDocument updated = editor.setParent(requireDocument(), nodeId, newParentId, Instant.now());
        persist(updated);
    }

    public void setParentAndIndex(String nodeId, String newParentId, int index) {
        TraceDocument updated = editor.setParentAndIndex(requireDocument(), nodeId, newParentId, index, Instant.now());
        persist(updated);
    }

    public void setExpandedNodes(Set<String> expandedNodeIds) {
        if (requireDocument().expandedNodeIds().equals(expandedNodeIds)) return;
        TraceDocument updated = editor.setExpandedNodeIds(requireDocument(), expandedNodeIds, Instant.now());
        persist(updated);
    }

    private TraceDocument requireDocument() {
        return Objects.requireNonNull(state.currentDocument(), "currentDocument");
    }

    private void persist(TraceDocument document) {
        if (state.currentFileName() == null) {
            throw new IllegalStateException("No selected file");
        }
        storage.save(state.currentFileName(), document);
        state.replaceDocument(storage.load(state.currentFileName()));
    }

    private static TraceDocument createEmptyDocument(String name) {
        Instant now = Instant.now();
        return new TraceDocument(
                3,
                "trace-" + UUID.randomUUID(),
                name,
                "",
                now,
                now,
                List.of(),
                List.of(),
                Set.of());
    }

    private static int indexOfNode(List<TraceNode> nodes, String nodeId) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).id().equals(nodeId)) {
                return i;
            }
        }
        return -1;
    }

    private static TraceDocument moveInternal(TraceDocument document, String nodeId, int offset, Instant now) {
        if (offset == 0) {
            return document;
        }
        List<TraceNode> nodes = new ArrayList<>(document.nodes());
        int sourceIndex = indexOfNode(nodes, nodeId);
        if (sourceIndex < 0) {
            return document;
        }
        int targetIndex = sourceIndex + offset;
        if (targetIndex < 0 || targetIndex >= nodes.size()) {
            return document;
        }
        TraceNode moved = nodes.remove(sourceIndex);
        nodes.add(targetIndex, moved);
        return new TraceDocument(
                3,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                List.copyOf(nodes),
                document.links(),
                document.expandedNodeIds());
    }

    private TraceDocument insertOrReuseNodeAfter(TraceDocument document, TraceNode candidate, String afterNodeId, Instant now) {
        List<TraceNode> nodes = document.nodes();
        // Check if candidate already exists
        for (int i = 0; i < nodes.size(); i++) {
            TraceNode existing = nodes.get(i);
            if (existing.displayName().equals(candidate.displayName())
                    && existing.filePath().equals(candidate.filePath())
                    && existing.line() == candidate.line()) {
                if (afterNodeId != null) {
                    int afterIndex = indexOfNode(nodes, afterNodeId);
                    if (afterIndex >= 0) {
                        String sameParent = nodes.get(afterIndex).parentId();
                        int targetIdx = afterIndex + 1;
                        boolean needsReparent = !Objects.equals(existing.parentId(), sameParent);
                        if (i != targetIdx || needsReparent) {
                            if (needsReparent) {
                                // Different parent: reparent to sibling level
                                int afterSiblingIdx = 0;
                                for (int j = 0; j < afterIndex; j++) {
                                    if (Objects.equals(nodes.get(j).parentId(), sameParent)) {
                                        afterSiblingIdx++;
                                    }
                                }
                                return editor.setParentAndIndex(document, existing.id(), sameParent, afterSiblingIdx + 1, now);
                            }
                            return moveInternalToIndex(document, existing.id(), targetIdx, now);
                        }
                    }
                }
                return document;
            }
        }
        // Find the parent of the focused node for same-level insertion
        String parentId = null;
        int insertIdx = nodes.size(); // default: append to end
        if (afterNodeId != null) {
            for (int i = 0; i < nodes.size(); i++) {
                if (nodes.get(i).id().equals(afterNodeId)) {
                    parentId = nodes.get(i).parentId();
                    insertIdx = i + 1;
                    break;
                }
            }
        }
        TraceNode withId = new TraceNode(
                "node-" + UUID.randomUUID(),
                candidate.displayName(),
                candidate.qualifiedName(),
                candidate.signature(),
                candidate.filePath(),
                candidate.line(),
                candidate.language(),
                candidate.note(),
                candidate.navigationHint(),
                parentId,
                candidate.title());
        return editor.insertNodeAt(document, withId, insertIdx, now);
    }

    private static TraceDocument moveInternalToIndex(TraceDocument document, String nodeId, int targetIndex, Instant now) {
        List<TraceNode> nodes = new ArrayList<>(document.nodes());
        int currentIndex = indexOfNode(nodes, nodeId);
        if (currentIndex < 0) {
            return document;
        }
        int boundedTarget = Math.max(0, Math.min(targetIndex, nodes.size() - 1));
        if (currentIndex == boundedTarget) {
            return document;
        }
        TraceNode moved = nodes.remove(currentIndex);
        // After removal, indices shift for nodes after the removed one
        int adjustedTarget = currentIndex < boundedTarget ? boundedTarget - 1 : boundedTarget;
        adjustedTarget = Math.max(0, Math.min(adjustedTarget, nodes.size()));
        nodes.add(adjustedTarget, moved);
        return new TraceDocument(
                3,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                List.copyOf(nodes),
                document.links(),
                document.expandedNodeIds());
    }

    private static String resolveInsertedNodeId(TraceDocument document, TraceNode candidate) {
        for (TraceNode node : document.nodes()) {
            if (node.displayName().equals(candidate.displayName())
                    && node.filePath().equals(candidate.filePath())
                    && node.line() == candidate.line()) {
                return node.id();
            }
        }
        throw new IllegalStateException("Inserted node not found in document");
    }

    private static TraceDocument deleteInternal(TraceDocument document, String nodeId, Instant now) {
        Set<String> allRemoved = new HashSet<>();
        allRemoved.add(nodeId);
        // Cascade: collect all descendant IDs
        collectDescendantIds(document.nodes(), allRemoved);
        List<TraceNode> nodes = document.nodes().stream()
                .filter(node -> !allRemoved.contains(node.id()))
                .toList();
        // Keep all links — they are used for navigation, not structural grouping
        List<TraceLink> links = document.links();
        return new TraceDocument(
                3,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                nodes,
                links,
                document.expandedNodeIds());
    }

    private static void collectDescendantIds(List<TraceNode> nodes, Set<String> result) {
        Set<String> current = new HashSet<>(result);
        for (TraceNode node : nodes) {
            if (node.parentId() != null && current.contains(node.parentId())) {
                result.add(node.id());
            }
        }
        if (result.size() > current.size()) {
            collectDescendantIds(nodes, result);
        }
    }
}
