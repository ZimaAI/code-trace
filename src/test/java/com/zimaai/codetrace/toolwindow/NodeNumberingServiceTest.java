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

    @Test
    void calculateNumbers_shouldNumberNestedNodes() {
        // Given
        TraceNode root1 = createNode("root-1", "Root1", null);
        TraceNode child1 = createNode("child-1", "Child1", "root-1");
        TraceNode child2 = createNode("child-2", "Child2", "root-1");
        TraceNode grandchild = createNode("grandchild-1", "GrandChild1", "child-1");
        TraceNode root2 = createNode("root-2", "Root2", null);

        TraceDocument doc = createDocument(
                List.of(root1, child1, child2, grandchild, root2),
                List.of());

        // When
        Map<String, String> numbers = NodeNumberingService.calculateNumbers(doc);

        // Then
        assertEquals("1", numbers.get("root-1"));
        assertEquals("1.1", numbers.get("child-1"));
        assertEquals("1.2", numbers.get("child-2"));
        assertEquals("1.1.1", numbers.get("grandchild-1"));
        assertEquals("2", numbers.get("root-2"));
    }

    @Test
    void calculateNumbers_shouldHandleEmptyDocument() {
        // Given
        TraceDocument doc = createDocument(List.of(), List.of());

        // When
        Map<String, String> numbers = NodeNumberingService.calculateNumbers(doc);

        // Then
        assertEquals(true, numbers.isEmpty());
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
