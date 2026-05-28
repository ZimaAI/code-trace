package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import java.util.ArrayList;
import java.util.List;

public final class CodeTraceState {
    private String currentFileName;
    private TraceDocument currentDocument;
    private boolean dirty;
    private boolean autoSaveEnabled;
    private boolean dedupEnabled = true;
    private final List<UnsavedChangesDecision> decisions = new ArrayList<>();

    public String currentFileName() {
        return currentFileName;
    }

    public TraceDocument currentDocument() {
        return currentDocument;
    }

    public boolean dirty() {
        return dirty;
    }

    public boolean autoSaveEnabled() {
        return autoSaveEnabled;
    }

    public boolean dedupEnabled() {
        return dedupEnabled;
    }

    public List<UnsavedChangesDecision> dirtyHistory() {
        return List.copyOf(decisions);
    }

    void load(String fileName, TraceDocument document) {
        this.currentFileName = fileName;
        this.currentDocument = document;
        this.dirty = false;
    }

    void markDirty(TraceDocument document) {
        this.currentDocument = document;
        this.dirty = true;
    }

    void markSaved(TraceDocument document) {
        this.currentDocument = document;
        this.dirty = false;
    }

    void setAutoSaveEnabled(boolean autoSaveEnabled) {
        this.autoSaveEnabled = autoSaveEnabled;
    }

    void setDedupEnabled(boolean dedupEnabled) {
        this.dedupEnabled = dedupEnabled;
    }

    void recordDecision(UnsavedChangesDecision decision) {
        decisions.add(decision);
    }
}
