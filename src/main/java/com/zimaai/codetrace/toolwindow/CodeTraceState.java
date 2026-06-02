package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;

public final class CodeTraceState {
    private String currentFileName;
    private TraceDocument currentDocument;
    private String pendingLinkSourceId;
    private String preferredSelectedNodeId;
    private String focusedNodeId;

    public String currentFileName() {
        return currentFileName;
    }

    public TraceDocument currentDocument() {
        return currentDocument;
    }

    public String pendingLinkSourceId() {
        return pendingLinkSourceId;
    }

    public String preferredSelectedNodeId() {
        return preferredSelectedNodeId;
    }

    public String focusedNodeId() {
        return focusedNodeId;
    }

    void load(String fileName, TraceDocument document) {
        this.currentFileName = fileName;
        this.currentDocument = document;
        this.pendingLinkSourceId = null;
        this.preferredSelectedNodeId = null;
        this.focusedNodeId = null;
    }

    void replaceDocument(TraceDocument document) {
        this.currentDocument = document;
    }

    void setPendingLinkSourceId(String pendingLinkSourceId) {
        this.pendingLinkSourceId = pendingLinkSourceId;
    }

    void clearPendingLinkSource() {
        this.pendingLinkSourceId = null;
    }

    void setPreferredSelectedNodeId(String preferredSelectedNodeId) {
        this.preferredSelectedNodeId = preferredSelectedNodeId;
    }

    String consumePreferredSelectedNodeId() {
        String preferred = preferredSelectedNodeId;
        preferredSelectedNodeId = null;
        return preferred;
    }

    void setFocusedNodeId(String focusedNodeId) {
        this.focusedNodeId = focusedNodeId;
    }

    void clearFocusedNodeId() {
        this.focusedNodeId = null;
    }
}
