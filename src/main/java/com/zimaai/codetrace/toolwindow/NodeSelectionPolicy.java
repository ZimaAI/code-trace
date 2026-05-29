package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceNode;
import java.util.List;

final class NodeSelectionPolicy {
    private NodeSelectionPolicy() {
    }

    static String resolveSelectedNodeId(List<TraceNode> nodes, String previousSelectedNodeId, String preferredSelectedNodeId) {
        if (nodes.isEmpty()) {
            return null;
        }

        if (preferredSelectedNodeId != null && containsNode(nodes, preferredSelectedNodeId)) {
            return preferredSelectedNodeId;
        }

        if (previousSelectedNodeId != null && containsNode(nodes, previousSelectedNodeId)) {
            return previousSelectedNodeId;
        }

        return null;
    }

    static int indexOfNode(List<TraceNode> nodes, String nodeId) {
        if (nodeId == null) {
            return -1;
        }
        for (int i = 0; i < nodes.size(); i++) {
            if (nodeId.equals(nodes.get(i).id())) {
                return i;
            }
        }
        return -1;
    }

    private static boolean containsNode(List<TraceNode> nodes, String nodeId) {
        return indexOfNode(nodes, nodeId) >= 0;
    }
}
