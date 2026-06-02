package com.zimaai.codetrace.model;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record TraceDocument(
        int schemaVersion,
        String id,
        String name,
        String description,
        Instant createdAt,
        Instant updatedAt,
        List<TraceNode> nodes,
        List<TraceLink> links,
        Set<String> expandedNodeIds) {
}
