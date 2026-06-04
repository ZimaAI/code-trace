package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests for expand/collapse state management in TraceDocumentEditor.
 */
class TraceDocumentEditorTest {

    private static final Instant NOW = Instant.now();
    private final TraceDocumentEditor editor = new TraceDocumentEditor();

    private static TraceDocument createTestDocument(Set<String> expandedNodeIds) {
        return new TraceDocument(3, "doc-1", "test", "", NOW, NOW,
                List.of(), List.of(), new HashSet<>(expandedNodeIds));
    }

    private static TraceNode root(String id) {
        return new TraceNode(id, id, "", "", "", 0, "", "", "", null, null);
    }

    private static TraceNode child(String id, String parentId) {
        return new TraceNode(id, id, "", "", "", 0, "", "", "", parentId, null);
    }

    @Test
    void testToggleExpandedNode_AddsNodeId() {
        TraceDocument doc = createTestDocument(Set.of());

        TraceDocument result = editor.toggleExpandedNode(doc, "node1", true, NOW);

        assertTrue(result.expandedNodeIds().contains("node1"));
    }

    @Test
    void testToggleExpandedNode_RemovesNodeId() {
        TraceDocument doc = createTestDocument(Set.of("node1"));

        TraceDocument result = editor.toggleExpandedNode(doc, "node1", false, NOW);

        assertFalse(result.expandedNodeIds().contains("node1"));
    }

    @Test
    void testExpandAllNodes_ExpandsAllParentNodes() {
        List<TraceNode> nodes = List.of(
                root("1"),
                child("2", "1"));
        TraceDocument doc = new TraceDocument(3, "test", "test", "test",
                NOW, NOW, nodes, List.of(), Set.of());

        TraceDocument result = editor.expandAllNodes(doc, NOW);

        assertTrue(result.expandedNodeIds().contains("1"));
    }

    @Test
    void testCollapseAllNodes_ClearsExpandedNodeIds() {
        TraceDocument doc = createTestDocument(Set.of("node1", "node2"));

        TraceDocument result = editor.collapseAllNodes(doc, NOW);

        assertTrue(result.expandedNodeIds().isEmpty());
    }
}
