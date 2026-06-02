package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TraceTreeModelTest {

    @Test
    void virtualRootChildrenAreAllTopLevelNodes() {
        TraceDocument doc = new TraceDocument(
                3, "t1", "T1", "", Instant.now(), Instant.now(),
                List.of(
                        new TraceNode("n1", "a", "", "", "", 0, "", "", ""),
                        new TraceNode("n2", "b", "", "", "", 0, "", "", ""),
                        new TraceNode("n3", "c", "", "", "", 0, "", "", "", "n1", null)),
                List.of(), Set.of());

        TraceTreeModel model = new TraceTreeModel(() -> doc);
        Object root = model.getRoot();
        assertEquals(2, model.getChildCount(root)); // n1, n2 (not n3 since it's a child of n1)
        assertEquals("n1", ((TraceNode) model.getChild(root, 0)).id());
        assertEquals("n2", ((TraceNode) model.getChild(root, 1)).id());
    }

    @Test
    void childCountMatchesChildrenOfNode() {
        TraceDocument doc = new TraceDocument(
                3, "t2", "T2", "", Instant.now(), Instant.now(),
                List.of(
                        new TraceNode("n1", "parent", "", "", "", 0, "", "", ""),
                        new TraceNode("n2", "child1", "", "", "", 0, "", "", "", "n1", null),
                        new TraceNode("n3", "child2", "", "", "", 0, "", "", "", "n1", null)),
                List.of(), Set.of());

        TraceTreeModel model = new TraceTreeModel(() -> doc);
        TraceNode n1 = doc.nodes().get(0);
        assertEquals(2, model.getChildCount(n1));
    }

    @Test
    void leafNodesHaveNoChildren() {
        TraceDocument doc = new TraceDocument(
                3, "t3", "T3", "", Instant.now(), Instant.now(),
                List.of(new TraceNode("n1", "leaf", "", "", "", 0, "", "", "")),
                List.of(), Set.of());

        TraceTreeModel model = new TraceTreeModel(() -> doc);
        TraceNode n1 = doc.nodes().get(0);
        assertTrue(model.isLeaf(n1));
    }

    @Test
    void handlesNullDocumentGracefully() {
        TraceTreeModel model = new TraceTreeModel(() -> null);
        assertEquals(0, model.getChildCount(model.getRoot()));
    }
}
