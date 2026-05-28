package com.zimaai.codetrace.model;

import java.time.Instant;
import java.util.List;

public record TraceDocument(
        int schemaVersion,
        String id,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt,
        List<TraceNode> nodes,
        List<TraceLink> links) {
}
