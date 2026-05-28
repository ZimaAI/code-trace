package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;

public final class CodeTraceState {
    private String currentFileName;
    private TraceDocument currentDocument;
    private String pendingLinkSourceId;

    public String currentFileName() {
        return currentFileName;
    }

    public TraceDocument currentDocument() {
        return currentDocument;
    }

    public String pendingLinkSourceId() {
        return pendingLinkSourceId;
    }

    void load(String fileName, TraceDocument document) {
        this.currentFileName = fileName;
        this.currentDocument = document;
        this.pendingLinkSourceId = null;
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
}
