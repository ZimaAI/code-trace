package com.zimaai.codetrace.model;

import java.time.Instant;

public record TraceLink(
        String id,
        String sourceNodeId,
        String targetNodeId,
        Instant createdAt,
        TraceLinkKind kind) {
}
