package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class FilteredNodeTableModelTest {

    private List<TraceNode> nodes;
    private NodeTableModel sourceModel;
    private TraceDocument document;

    @BeforeEach
    void setUp() {
        nodes = List.of(
            new TraceNode("1", "Root1", null, null, null, 0, null, null, null, null, null),
            new TraceNode("2", "Child1", null, null, null, 0, null, null, null, "1", null),
            new TraceNode("3", "Child2", null, null, null, 0, null, null, null, "1", null),
            new TraceNode("4", "Root2", null, null, null, 0, null, null, null, null, null)
        );
        Map<String, String> numberMap = Map.of("1", "1", "2", "1.1", "3", "1.2", "4", "2");
        sourceModel = new NodeTableModel(nodes, numberMap, List.of());
        document = new TraceDocument(3, "test", "test", "test", Instant.now(), Instant.now(), nodes, List.of(), Set.of());
    }

    @Test
    void testGetRowCount_AllExpanded_ReturnsAllNodes() {
        // All expanded
        document = new TraceDocument(3, "test", "test", "test", Instant.now(), Instant.now(), nodes, List.of(), Set.of("1", "4"));
        FilteredNodeTableModel filteredModel = new FilteredNodeTableModel(sourceModel, document);

        assertEquals(4, filteredModel.getRowCount());
    }

    @Test
    void testGetRowCount_ParentCollapsed_HidesChildren() {
        // Root1 collapsed
        FilteredNodeTableModel filteredModel = new FilteredNodeTableModel(sourceModel, document);

        // Should show Root1 (collapsed) and Root2
        assertEquals(2, filteredModel.getRowCount());
    }

    @Test
    void testGetNodeAt_ParentCollapsed_ReturnsCorrectNodes() {
        FilteredNodeTableModel filteredModel = new FilteredNodeTableModel(sourceModel, document);

        TraceNode firstNode = filteredModel.getNodeAt(0);
        assertEquals("1", firstNode.id());

        TraceNode secondNode = filteredModel.getNodeAt(1);
        assertEquals("4", secondNode.id());
    }

    @Test
    void testIsVisible_ParentCollapsed_ChildrenNotVisible() {
        FilteredNodeTableModel filteredModel = new FilteredNodeTableModel(sourceModel, document);

        assertFalse(filteredModel.isVisible(nodes.get(1))); // Child1
        assertFalse(filteredModel.isVisible(nodes.get(2))); // Child2
    }

    @Test
    void testIsVisible_ParentExpanded_ChildrenVisible() {
        document = new TraceDocument(3, "test", "test", "test", Instant.now(), Instant.now(), nodes, List.of(), Set.of("1", "4"));
        FilteredNodeTableModel filteredModel = new FilteredNodeTableModel(sourceModel, document);

        assertTrue(filteredModel.isVisible(nodes.get(1))); // Child1
        assertTrue(filteredModel.isVisible(nodes.get(2))); // Child2
    }

    @Test
    void testRebuildVisibleNodes_UpdatesAfterStateChange() {
        FilteredNodeTableModel filteredModel = new FilteredNodeTableModel(sourceModel, document);
        assertEquals(2, filteredModel.getRowCount());

        // Expand Root1
        document = new TraceDocument(3, "test", "test", "test", Instant.now(), Instant.now(), nodes, List.of(), Set.of("1", "4"));
        filteredModel.setDocument(document);
        filteredModel.rebuildVisibleNodes();

        assertEquals(4, filteredModel.getRowCount());
    }
}
