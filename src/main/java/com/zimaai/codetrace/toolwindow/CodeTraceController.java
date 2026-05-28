package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.model.TraceVersion;
import com.zimaai.codetrace.model.TraceVersionSource;
import com.zimaai.codetrace.recording.TraceRecordingService;
import com.zimaai.codetrace.storage.TraceStorageService;
import java.util.ArrayList;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

public final class CodeTraceController {
    private final TraceStorageService storage;
    private final Function<UnsavedChangesDecision, Boolean> refreshPermission;
    private final TraceRecordingService recordingService;
    private final Function<TraceNode, Boolean> navigationHandler;
    private final CodeTraceState state = new CodeTraceState();

    public CodeTraceController(
            TraceStorageService storage,
            Function<UnsavedChangesDecision, Boolean> refreshPermission,
            TraceRecordingService recordingService,
            Function<TraceNode, Boolean> navigationHandler) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.refreshPermission = Objects.requireNonNull(refreshPermission, "refreshPermission");
        this.recordingService = Objects.requireNonNull(recordingService, "recordingService");
        this.navigationHandler = Objects.requireNonNull(navigationHandler, "navigationHandler");
    }

    public List<String> loadFileNames() {
        return storage.listFiles();
    }

    public void loadFile(String fileName, TraceDocument document) {
        state.load(fileName, document);
    }

    public TraceDocument load(String fileName) {
        TraceDocument document = storage.load(fileName);
        state.load(fileName, document);
        return document;
    }

    public boolean refreshCurrentFile() {
        if (state.currentFileName() == null) {
            return false;
        }
        if (state.dirty()) {
            UnsavedChangesDecision decision = UnsavedChangesDecision.DISCARD;
            state.recordDecision(decision);
            boolean confirmed = refreshPermission.apply(decision);
            if (!confirmed) {
                return false;
            }
        }
        state.load(state.currentFileName(), storage.load(state.currentFileName()));
        return true;
    }

    public boolean refreshWithDecision(UnsavedChangesDecision decision) {
        if (state.currentFileName() == null) {
            return false;
        }
        if (!state.dirty()) {
            state.load(state.currentFileName(), storage.load(state.currentFileName()));
            return true;
        }
        if (decision == null) {
            return false;
        }
        recordDecision(decision);
        if (decision == UnsavedChangesDecision.CANCEL) {
            return false;
        }
        if (decision == UnsavedChangesDecision.SAVE) {
            saveCurrentFile();
        }
        if (decision != UnsavedChangesDecision.DISCARD && decision != UnsavedChangesDecision.SAVE) {
            return false;
        }
        state.load(state.currentFileName(), storage.load(state.currentFileName()));
        return true;
    }

    public boolean hasUnsavedChanges() {
        return state.dirty();
    }

    public void recordDecision(UnsavedChangesDecision decision) {
        if (decision != null) {
            state.recordDecision(decision);
        }
    }

    public void saveCurrentFile() {
        if (state.currentFileName() == null || state.currentDocument() == null) {
            return;
        }
        storage.save(state.currentFileName(), state.currentDocument());
        state.markSaved(state.currentDocument());
    }

    public void updateDescription(String description) {
        TraceDocument current = state.currentDocument();
        if (current == null) {
            return;
        }
        TraceDocument updated = new TraceDocument(
                current.schemaVersion(),
                current.id(),
                current.name(),
                description,
                current.createdAt(),
                Instant.now(),
                current.current(),
                current.history());
        state.markDirty(updated);
        if (state.autoSaveEnabled()) {
            saveCurrentFile();
        }
    }

    public void updateNodeNote(int nodeIndex, String note) {
        TraceDocument document = state.currentDocument();
        if (document == null) {
            return;
        }
        TraceVersion currentVersion = ensureCurrentVersion(document);
        List<TraceNode> nodes = new ArrayList<>(currentVersion.nodes());
        if (nodeIndex < 0 || nodeIndex >= nodes.size()) {
            return;
        }
        TraceNode node = nodes.get(nodeIndex);
        TraceNode updatedNode = new TraceNode(
                node.id(),
                node.displayName(),
                node.qualifiedName(),
                node.signature(),
                node.filePath(),
                node.line(),
                node.language(),
                note,
                node.navigationHint());
        nodes.set(nodeIndex, updatedNode);
        TraceVersion updatedVersion = new TraceVersion(
                currentVersion.versionId(),
                currentVersion.source(),
                currentVersion.recordedAt(),
                Instant.now(),
                currentVersion.nodeDedupEnabled(),
                List.copyOf(nodes));
        state.markDirty(replaceCurrent(document, updatedVersion));
        if (state.autoSaveEnabled()) {
            saveCurrentFile();
        }
    }

    public int addNode(TraceNode node) {
        TraceDocument document = state.currentDocument();
        if (document == null || node == null) {
            return -1;
        }
        TraceVersion currentVersion = ensureCurrentVersion(document);
        List<TraceNode> nodes = new ArrayList<>(currentVersion.nodes());
        nodes.add(node);
        TraceVersion updatedVersion = new TraceVersion(
                currentVersion.versionId(),
                currentVersion.source(),
                currentVersion.recordedAt(),
                Instant.now(),
                currentVersion.nodeDedupEnabled(),
                List.copyOf(nodes));
        state.markDirty(replaceCurrent(document, updatedVersion));
        if (state.autoSaveEnabled()) {
            saveCurrentFile();
        }
        return nodes.size() - 1;
    }

    public void updateNode(int nodeIndex, TraceNode node) {
        TraceDocument document = state.currentDocument();
        if (document == null || node == null) {
            return;
        }
        TraceVersion currentVersion = ensureCurrentVersion(document);
        List<TraceNode> nodes = new ArrayList<>(currentVersion.nodes());
        if (nodeIndex < 0 || nodeIndex >= nodes.size()) {
            return;
        }
        nodes.set(nodeIndex, node);
        TraceVersion updatedVersion = new TraceVersion(
                currentVersion.versionId(),
                currentVersion.source(),
                currentVersion.recordedAt(),
                Instant.now(),
                currentVersion.nodeDedupEnabled(),
                List.copyOf(nodes));
        state.markDirty(replaceCurrent(document, updatedVersion));
        if (state.autoSaveEnabled()) {
            saveCurrentFile();
        }
    }

    public void deleteNode(int nodeIndex) {
        TraceDocument document = state.currentDocument();
        if (document == null) {
            return;
        }
        TraceVersion currentVersion = ensureCurrentVersion(document);
        List<TraceNode> nodes = new ArrayList<>(currentVersion.nodes());
        if (nodeIndex < 0 || nodeIndex >= nodes.size()) {
            return;
        }
        nodes.remove(nodeIndex);
        TraceVersion updatedVersion = new TraceVersion(
                currentVersion.versionId(),
                currentVersion.source(),
                currentVersion.recordedAt(),
                Instant.now(),
                currentVersion.nodeDedupEnabled(),
                List.copyOf(nodes));
        state.markDirty(replaceCurrent(document, updatedVersion));
        if (state.autoSaveEnabled()) {
            saveCurrentFile();
        }
    }

    public int moveNode(int nodeIndex, int offset) {
        TraceDocument document = state.currentDocument();
        if (document == null || offset == 0) {
            return nodeIndex;
        }
        TraceVersion currentVersion = ensureCurrentVersion(document);
        List<TraceNode> nodes = new ArrayList<>(currentVersion.nodes());
        if (nodeIndex < 0 || nodeIndex >= nodes.size()) {
            return nodeIndex;
        }
        int targetIndex = nodeIndex + offset;
        if (targetIndex < 0 || targetIndex >= nodes.size()) {
            return nodeIndex;
        }
        TraceNode node = nodes.remove(nodeIndex);
        nodes.add(targetIndex, node);
        TraceVersion updatedVersion = new TraceVersion(
                currentVersion.versionId(),
                currentVersion.source(),
                currentVersion.recordedAt(),
                Instant.now(),
                currentVersion.nodeDedupEnabled(),
                List.copyOf(nodes));
        state.markDirty(replaceCurrent(document, updatedVersion));
        if (state.autoSaveEnabled()) {
            saveCurrentFile();
        }
        return targetIndex;
    }

    public void setAutoSaveEnabled(boolean enabled) {
        state.setAutoSaveEnabled(enabled);
    }

    public void setDedupEnabled(boolean enabled) {
        state.setDedupEnabled(enabled);
    }

    public void startRecording() {
        recordingService.start(state.dedupEnabled());
    }

    public void recordNavigation(com.zimaai.codetrace.recording.TraceableNavigationTarget target) {
        recordingService.record(target);
    }

    public void stopRecording() {
        TraceDocument current = state.currentDocument();
        if (current == null) {
            return;
        }
        TraceDocument updated = recordingService.stop(current);
        state.markDirty(updated);
        if (state.autoSaveEnabled()) {
            saveCurrentFile();
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
        TraceDocument document = TraceDocumentFactory.create(displayName);
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

    public TraceStorageService storage() {
        return storage;
    }

    private static final class TraceDocumentFactory {
        private TraceDocumentFactory() {
        }

        private static TraceDocument create(String name) {
            Instant now = Instant.now();
            return new TraceDocument(
                    1,
                    "trace-" + UUID.randomUUID(),
                    name,
                    "",
                    now,
                    now,
                    null,
                    List.of());
        }
    }

    private TraceVersion ensureCurrentVersion(TraceDocument document) {
        if (document.current() != null) {
            return document.current();
        }
        Instant now = Instant.now();
        TraceVersion created = new TraceVersion(
                "v-" + UUID.randomUUID(),
                TraceVersionSource.MANUAL,
                now,
                now,
                state.dedupEnabled(),
                List.of());
        state.markDirty(replaceCurrent(document, created));
        return created;
    }

    private static TraceDocument replaceCurrent(TraceDocument document, TraceVersion version) {
        return new TraceDocument(
                document.schemaVersion(),
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                Instant.now(),
                version,
                document.history());
    }
}
