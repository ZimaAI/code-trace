package com.zimaai.codetrace.model;

public record TraceNode(
        String id,
        String displayName,
        String qualifiedName,
        String signature,
        String filePath,
        int line,
        String language,
        String note,
        String navigationHint,
        String parentId,
        String title) {
    /** Convenience constructor for root-level nodes without title. */
    public TraceNode(
            String id,
            String displayName,
            String qualifiedName,
            String signature,
            String filePath,
            int line,
            String language,
            String note,
            String navigationHint) {
        this(id, displayName, qualifiedName, signature, filePath, line, language, note, navigationHint, null, null);
    }
}
