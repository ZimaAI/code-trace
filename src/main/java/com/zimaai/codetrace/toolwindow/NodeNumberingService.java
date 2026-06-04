package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class NodeNumberingService {
    private NodeNumberingService() {
        // Utility class
    }

    public static Map<String, String> calculateNumbers(TraceDocument document) {
        Objects.requireNonNull(document, "document");
        Map<String, String> numbers = new HashMap<>();
        List<TraceNode> nodes = document.nodes();

        int rootIndex = 1;
        for (TraceNode node : nodes) {
            if (node.parentId() == null) {
                String rootNumber = String.valueOf(rootIndex);
                numbers.put(node.id(), rootNumber);
                calculateChildNumbers(nodes, node.id(), rootNumber, numbers);
                rootIndex++;
            }
        }

        return numbers;
    }

    private static void calculateChildNumbers(
            List<TraceNode> nodes,
            String parentId,
            String parentNumber,
            Map<String, String> numbers) {
        int childIndex = 1;
        for (TraceNode node : nodes) {
            if (parentId.equals(node.parentId())) {
                String childNumber = parentNumber + "." + childIndex;
                numbers.put(node.id(), childNumber);
                calculateChildNumbers(nodes, node.id(), childNumber, numbers);
                childIndex++;
            }
        }
    }
}
