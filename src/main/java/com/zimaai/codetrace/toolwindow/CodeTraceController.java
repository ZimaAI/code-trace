package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.storage.TraceStorageService;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
        TraceDocument updated = editor.addNode(requireDocument(), node, Instant.now());
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

    public int moveNodeOrPair(String nodeId, int offset) {
        TraceDocument updated = moveInternal(requireDocument(), nodeId, offset, Instant.now());
        persist(updated);
        return indexOfNode(updated.nodes(), nodeId);
    }

    public void deleteNodeOrPair(String nodeId) {
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
                2,
                "trace-" + UUID.randomUUID(),
                name,
                "",
                now,
                now,
                List.of(),
                List.of());
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
        List<String> affectedIds = linkedNodeIds(document.links(), nodeId);
        List<Integer> sourceIndexes = new ArrayList<>();
        for (String affectedId : affectedIds) {
            int index = indexOfNode(nodes, affectedId);
            if (index < 0) {
                return document;
            }
            sourceIndexes.add(index);
        }
        List<Integer> targetIndexes = sourceIndexes.stream().map(index -> index + offset).toList();
        for (Integer targetIndex : targetIndexes) {
            if (targetIndex < 0 || targetIndex >= nodes.size()) {
                return document;
            }
        }
        List<Integer> orderedIndexes = new ArrayList<>(sourceIndexes);
        orderedIndexes.sort(Comparator.naturalOrder());
        if (offset > 0) {
            for (int i = orderedIndexes.size() - 1; i >= 0; i--) {
                int source = orderedIndexes.get(i);
                TraceNode moved = nodes.remove(source);
                nodes.add(source + offset, moved);
            }
        } else {
            for (int source : orderedIndexes) {
                TraceNode moved = nodes.remove(source);
                nodes.add(source + offset, moved);
            }
        }
        return new TraceDocument(
                2,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                List.copyOf(nodes),
                document.links());
    }

    private static TraceDocument deleteInternal(TraceDocument document, String nodeId, Instant now) {
        List<String> affectedIds = linkedNodeIds(document.links(), nodeId);
        List<TraceNode> nodes = document.nodes().stream()
                .filter(node -> !affectedIds.contains(node.id()))
                .toList();
        List<TraceLink> links = document.links().stream()
                .filter(link -> !affectedIds.contains(link.sourceNodeId()) && !affectedIds.contains(link.targetNodeId()))
                .toList();
        return new TraceDocument(
                2,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                nodes,
                links);
    }

    private static List<String> linkedNodeIds(List<TraceLink> links, String nodeId) {
        for (TraceLink link : links) {
            if (link.sourceNodeId().equals(nodeId) || link.targetNodeId().equals(nodeId)) {
                return List.of(link.sourceNodeId(), link.targetNodeId());
            }
        }
        return List.of(nodeId);
    }
}
