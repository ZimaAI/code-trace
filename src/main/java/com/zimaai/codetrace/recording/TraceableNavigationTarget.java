package com.zimaai.codetrace.recording;

public record TraceableNavigationTarget(
        String id,
        String displayName,
        String qualifiedName,
        String signature,
        String filePath,
        int line,
        String language,
        String navigationHint) {
}
