package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceNode;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class NodeTableModelTest {

    @Test
    void exposesActionColumnWithNodeValue() {
        List<TraceNode> nodes = List.of(createNode("1", "Root", null));
        NodeTableModel model = new NodeTableModel(nodes, Map.of("1", "1"), List.of());

        assertEquals(4, model.getColumnCount());
        assertEquals("操作", model.getColumnName(3));
        assertEquals(TraceNode.class, model.getColumnClass(3));
        assertEquals(nodes.get(0), model.getValueAt(0, 3));
    }

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

    @Test
    void testGetDescendantIds_ReturnsAllDescendants() {
        List<TraceNode> nodes = List.of(
            createNode("1", "Root", null),
            createNode("2", "Child", "1"),
            createNode("3", "GrandChild", "2")
        );
        NodeTableModel model = new NodeTableModel(nodes, Map.of(), List.of());

        List<String> descendants = model.getDescendantIds("1");
        assertEquals(2, descendants.size());
        assertTrue(descendants.contains("2"));
        assertTrue(descendants.contains("3"));
    }

    @Test
    void testGetDescendantIds_NodeWithoutDescendants_ReturnsEmptyList() {
        List<TraceNode> nodes = List.of(
            createNode("1", "Root", null),
            createNode("2", "Child", "1")
        );
        NodeTableModel model = new NodeTableModel(nodes, Map.of(), List.of());

        List<String> descendants = model.getDescendantIds("2");
        assertTrue(descendants.isEmpty());
    }

    @Test
    void testGetNodeIndex_ReturnsCorrectIndex() {
        List<TraceNode> nodes = List.of(
            createNode("1", "First", null),
            createNode("2", "Second", null),
            createNode("3", "Third", null)
        );
        NodeTableModel model = new NodeTableModel(nodes, Map.of(), List.of());

        assertEquals(0, model.getNodeIndex("1"));
        assertEquals(1, model.getNodeIndex("2"));
        assertEquals(2, model.getNodeIndex("3"));
    }

    @Test
    void testGetNodeIndex_NonExistentNode_ReturnsMinusOne() {
        List<TraceNode> nodes = List.of(
            createNode("1", "Root", null)
        );
        NodeTableModel model = new NodeTableModel(nodes, Map.of(), List.of());

        assertEquals(-1, model.getNodeIndex("nonexistent"));
    }

    private static TraceNode createNode(String id, String displayName, String parentId) {
        return new TraceNode(
                id, displayName, displayName + "#qualified",
                displayName.toLowerCase() + "()",
                displayName + ".java", 1, "JAVA", "", "",
                parentId, null);
    }
}
