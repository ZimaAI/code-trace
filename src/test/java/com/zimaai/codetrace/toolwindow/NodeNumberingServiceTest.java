package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NodeNumberingServiceTest {

    @Test
    void calculateNumbers_shouldNumberRootNodes() {
        TraceNode node1 = createNode("node-1", "A", null);
        TraceNode node2 = createNode("node-2", "B", null);
        TraceNode node3 = createNode("node-3", "C", null);
        TraceDocument doc = createDocument(List.of(node1, node2, node3), List.of());

        Map<String, String> numbers = NodeNumberingService.calculateNumbers(doc);

        assertEquals("1", numbers.get("node-1"));
        assertEquals("2", numbers.get("node-2"));
        assertEquals("3", numbers.get("node-3"));
    }

    private static TraceNode createNode(String id, String displayName, String parentId) {
        return new TraceNode(
                id, displayName, displayName + "#m", displayName.toLowerCase() + "()",
                displayName + ".java", 1, "JAVA", "", displayName + "#m",
                parentId, null);
    }

    private static TraceDocument createDocument(List<TraceNode> nodes, List<?> links) {
        return new TraceDocument(
                2, "doc-1", "Test", "",
                Instant.EPOCH, Instant.EPOCH,
                nodes, List.of(), Set.of());
    }
}
