package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zimaai.codetrace.model.TraceNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class NodeSelectionPolicyTest {
    @Test
    void keepsPreviousSelectionWhenPreferredIsNull() {
        List<TraceNode> nodes = nodes();

        String resolved = NodeSelectionPolicy.resolveSelectedNodeId(nodes, "node-2", null);

        assertEquals("node-2", resolved);
    }

    @Test
    void usesPreferredSelectionWhenPresent() {
        List<TraceNode> nodes = nodes();

        String resolved = NodeSelectionPolicy.resolveSelectedNodeId(nodes, "node-1", "node-3");

        assertEquals("node-3", resolved);
    }

    @Test
    void clearsSelectionWhenNeitherPreferredNorPreviousExists() {
        List<TraceNode> nodes = nodes();

        String resolved = NodeSelectionPolicy.resolveSelectedNodeId(nodes, "node-missing", "node-also-missing");

        assertEquals(null, resolved);
    }

    @Test
    void clearsSelectionWhenNodeListEmpty() {
        String resolved = NodeSelectionPolicy.resolveSelectedNodeId(List.of(), "node-1", "node-2");

        assertEquals(null, resolved);
    }

    @Test
    void returnsNodeIndexOrMinusOne() {
        List<TraceNode> nodes = nodes();

        assertEquals(1, NodeSelectionPolicy.indexOfNode(nodes, "node-2"));
        assertEquals(-1, NodeSelectionPolicy.indexOfNode(nodes, "node-missing"));
        assertEquals(-1, NodeSelectionPolicy.indexOfNode(nodes, null));
    }

    private static List<TraceNode> nodes() {
        return List.of(
                new TraceNode("node-1", "line 1", "A#a", "a()", "A.java", 10, "JAVA", "", "A#a"),
                new TraceNode("node-2", "line 2", "B#b", "b()", "B.java", 20, "JAVA", "", "B#b"),
                new TraceNode("node-3", "line 3", "C#c", "c()", "C.java", 30, "JAVA", "", "C#c"));
    }
}
