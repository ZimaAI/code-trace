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
        String navigationHint) {
}
