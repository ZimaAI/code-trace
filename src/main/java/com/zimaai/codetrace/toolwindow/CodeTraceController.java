package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.recording.TraceRecordingService;
import com.zimaai.codetrace.storage.TraceStorageService;
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
}
