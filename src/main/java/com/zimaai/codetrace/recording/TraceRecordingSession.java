package com.zimaai.codetrace.recording;

import com.zimaai.codetrace.model.TraceNode;
import java.util.ArrayList;
import java.util.List;

final class TraceRecordingSession {
    private final boolean suppressConsecutiveDuplicates;
    private final List<TraceNode> nodes = new ArrayList<>();

    TraceRecordingSession(boolean suppressConsecutiveDuplicates) {
        this.suppressConsecutiveDuplicates = suppressConsecutiveDuplicates;
    }

    void record(TraceableNavigationTarget target) {
        TraceNode next = new TraceNode(
                target.id(),
                target.displayName(),
                target.qualifiedName(),
                target.signature(),
                target.filePath(),
                target.line(),
                target.language(),
                "",
                target.navigationHint());
        if (suppressConsecutiveDuplicates && !nodes.isEmpty()) {
            TraceNode last = nodes.get(nodes.size() - 1);
            boolean sameLocation = last.filePath().equals(next.filePath())
                    && last.line() == next.line()
                    && last.qualifiedName().equals(next.qualifiedName());
            if (sameLocation) {
                return;
            }
        }
        nodes.add(next);
    }

    List<TraceNode> snapshot() {
        return List.copyOf(nodes);
    }
}
