package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceNode;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NodeTableModelTest {

    @Test
    void testHasChildren_RootNodeWithChildren_ReturnsTrue() {
        List<TraceNode> nodes = List.of(
            createNode("1", "Root", null),
            createNode("2", "Child", "1")
        );
        NodeTableModel model = new NodeTableModel(nodes, Map.of(), List.of());

        assertTrue(model.hasChildren("1"));
    }

    @Test
    void testHasChildren_NodeWithoutChildren_ReturnsFalse() {
        List<TraceNode> nodes = List.of(
            createNode("1", "Root", null),
            createNode("2", "Child", "1")
        );
        NodeTableModel model = new NodeTableModel(nodes, Map.of(), List.of());

        assertFalse(model.hasChildren("2"));
    }

    @Test
    void testGetChildrenIds_ReturnsCorrectChildren() {
        List<TraceNode> nodes = List.of(
            createNode("1", "Root", null),
            createNode("2", "Child1", "1"),
            createNode("3", "Child2", "1"),
            createNode("4", "GrandChild", "2")
        );
        NodeTableModel model = new NodeTableModel(nodes, Map.of(), List.of());

        List<String> childrenIds = model.getChildrenIds("1");
        assertEquals(2, childrenIds.size());
        assertTrue(childrenIds.contains("2"));
        assertTrue(childrenIds.contains("3"));
    }

    @Test
    void testGetChildrenIds_NodeWithoutChildren_ReturnsEmptyList() {
        List<TraceNode> nodes = List.of(
            createNode("1", "Root", null)
        );
        NodeTableModel model = new NodeTableModel(nodes, Map.of(), List.of());

        List<String> childrenIds = model.getChildrenIds("1");
        assertTrue(childrenIds.isEmpty());
    }

    private static TraceNode createNode(String id, String displayName, String parentId) {
        return new TraceNode(
                id, displayName, displayName + "#qualified",
                displayName.toLowerCase() + "()",
                displayName + ".java", 1, "JAVA", "", "",
                parentId, null);
    }
}
