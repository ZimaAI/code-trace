package com.zimaai.codetrace.model;

import java.time.Instant;
import java.util.List;

public record TraceVersion(
        String versionId,
        TraceVersionSource source,
        Instant recordedAt,
        Instant updatedAt,
        boolean nodeDedupEnabled,
        List<TraceNode> nodes) {
}
