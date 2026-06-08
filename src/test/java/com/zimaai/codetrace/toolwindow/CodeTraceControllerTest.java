package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.storage.TraceJsonMapper;
import com.zimaai.codetrace.storage.TraceStorageService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeTraceControllerTest {
    @TempDir
    Path tempDir;

    @Test
    void savesTraceAndNodeNotesDirectlyToDisk() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-1.json", documentWithThreeNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);

        controller.load("trace-1.json");
        controller.saveDescription("saved trace note");
        controller.saveNodeNote("node-1", "saved node note");

        TraceDocument reloaded = storage.load("trace-1.json");
        assertEquals("saved trace note", reloaded.description());
        assertEquals("saved node note", reloaded.nodes().stream()
                .filter(node -> node.id().equals("node-1"))
                .findFirst()
                .orElseThrow()
                .note());
    }

    @Test
    void moveAndDeleteOnlyAffectSingleNode_notLinkedNodes() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-2.json", documentWithThreeNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);

        controller.load("trace-2.json");
        controller.setPendingLinkSource("node-1");
        controller.linkPendingSourceTo("node-2", TraceLinkKind.MANUAL);

        assertEquals(1, controller.state().currentDocument().links().size());

        // Move only node-2; node-1 stays in place
        int movedTo = controller.moveNode("node-2", 1);
        assertEquals(2, movedTo);
        List<String> order = controller.state().currentDocument().nodes().stream()
                .map(TraceNode::id)
                .collect(Collectors.toList());
        assertEquals(List.of("node-1", "node-3", "node-2"), order);

        // Delete only node-1; node-2 stays
        controller.deleteNode("node-1");
        assertEquals(List.of("node-3", "node-2"), controller.state().currentDocument().nodes().stream()
                .map(TraceNode::id)
                .collect(Collectors.toList()));
        // Links are preserved even when a referenced node is deleted
        assertEquals(1, controller.state().currentDocument().links().size());
    }

    @Test
    void moveNodeAtBoundaryKeepsOrder() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-boundary.json", documentWithThreeNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.load("trace-boundary.json");

        assertEquals(0, controller.moveNode("node-1", -1));
        assertEquals(List.of("node-1", "node-2", "node-3"),
                controller.state().currentDocument().nodes().stream().map(TraceNode::id).toList());

        assertEquals(2, controller.moveNode("node-3", 1));
        assertEquals(List.of("node-1", "node-2", "node-3"),
                controller.state().currentDocument().nodes().stream().map(TraceNode::id).toList());
    }

    @Test
    void rejectsSecondLinkForAlreadyLinkedNode() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-3.json", documentWithThreeNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);

        controller.load("trace-3.json");
        controller.setPendingLinkSource("node-1");
        controller.linkPendingSourceTo("node-2", TraceLinkKind.MANUAL);

        controller.setPendingLinkSource("node-1");
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> controller.linkPendingSourceTo("node-3", TraceLinkKind.MANUAL));

        assertEquals("Each node can participate in at most one link", exception.getMessage());
    }

    @Test
    void storesAndClearsPreferredSelectedNodeIdAcrossRefreshPaths() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-4.json", documentWithThreeNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);

        controller.load("trace-4.json");
        controller.preferSelectedNode("node-2");

        assertEquals("node-2", controller.state().preferredSelectedNodeId());
        assertEquals("node-2", controller.consumePreferredSelectedNodeId());
        assertNull(controller.state().preferredSelectedNodeId());

        controller.preferSelectedNode("node-3");
        controller.refreshCurrentFile();
        assertNull(controller.state().preferredSelectedNodeId());
    }

    @Test
    void insertsSourceAfterFocusedNodeAndAppendsWithoutFocus() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-1.json", documentWithThreeNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.load("trace-1.json");

        TraceNode source = new TraceNode(
                "temp-source",
                "source",
                "S#source",
                "source()",
                "src/S.java",
                40,
                "JAVA",
                "",
                "S#source");
        controller.setFocusedNodeId("node-2");
        int sourceIndex = controller.addOrReuseNodeAfterFocusedNode(source);
        String sourceId = controller.state().currentDocument().nodes().get(sourceIndex).id();

        assertEquals(List.of("node-1", "node-2", sourceId, "node-3"),
                controller.state().currentDocument().nodes().stream().map(TraceNode::id).toList());

        controller.clearFocusedNodeId();
        TraceNode tail = new TraceNode(
                "temp-tail",
                "tail",
                "T#tail",
                "tail()",
                "src/T.java",
                50,
                "JAVA",
                "",
                "T#tail");
        int tailIndex = controller.addOrReuseNodeAfterFocusedNode(tail);
        String tailId = controller.state().currentDocument().nodes().get(tailIndex).id();

        assertEquals(List.of("node-1", "node-2", sourceId, "node-3", tailId),
                controller.state().currentDocument().nodes().stream().map(TraceNode::id).toList());
    }

    @Test
    void reusesExistingSourceNodeAndMovesItAfterFocus() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-1.json", documentWithThreeNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.load("trace-1.json");

        TraceNode existing = controller.state().currentDocument().nodes().get(0);
        controller.setFocusedNodeId("node-2");
        int sourceIndex = controller.addOrReuseNodeAfterFocusedNode(existing);

        assertEquals(1, sourceIndex);
        assertEquals(List.of("node-2", "node-1", "node-3"),
                controller.state().currentDocument().nodes().stream().map(TraceNode::id).toList());
    }

    @Test
    void movesLinkedNodeGroupToExactIndexForDragReorder() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-1.json", linkedDocumentWithThreeNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.load("trace-1.json");

        controller.moveNodeToIndex("node-2", 0);

        // Only node-2 moves; node-3 stays in place
        assertEquals(List.of("node-2", "node-1", "node-3"),
                controller.state().currentDocument().nodes().stream().map(TraceNode::id).toList());
    }

    private static TraceDocument linkedDocumentWithThreeNodes() {
        return new TraceDocument(
                3,
                "trace-2",
                "Trace 2",
                "",
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(
                        new TraceNode("node-1", "line 1", "A#a", "a()", "A.java", 10, "JAVA", "", "A#a"),
                        new TraceNode("node-2", "line 2", "B#b", "b()", "B.java", 20, "JAVA", "", "B#b"),
                        new TraceNode("node-3", "line 3", "C#c", "c()", "C.java", 30, "JAVA", "", "C#c")),
                List.of(new TraceLink("link-1", "node-2", "node-3",
                        Instant.parse("2026-05-29T10:00:00Z"), TraceLinkKind.MANUAL)),
                Set.of());
    }

    @Test
    void moveNode_shouldOnlyMoveSingleNode_notLinkedNodes() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        TraceNode nodeA = new TraceNode("node-a", "A", "A#a", "a()", "A.java", 10, "JAVA", "", "A#a");
        TraceNode nodeB = new TraceNode("node-b", "B", "B#b", "b()", "B.java", 20, "JAVA", "", "B#b");
        TraceLink link = new TraceLink("link-1", "node-a", "node-b",
                        Instant.parse("2026-05-29T10:00:00Z"), TraceLinkKind.MANUAL);
        TraceDocument doc = new TraceDocument(
                3, "trace-linked", "Linked", "",
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(nodeA, nodeB),
                List.of(link),
                Set.of());
        storage.save("trace-linked.json", doc);
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.load("trace-linked.json");

        // When: move only node A by +1
        controller.moveNode("node-a", 1);

        // Then: only node A moved behind node B; node B stays at index 0
        List<String> order = controller.state().currentDocument().nodes().stream()
                .map(TraceNode::id).toList();
        assertEquals(List.of("node-b", "node-a"), order);
    }

    @Test
    void deleteNode_shouldOnlyDeleteSingleNode_notLinkedNodes() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        TraceNode nodeA = new TraceNode("node-a", "A", "A#a", "a()", "A.java", 10, "JAVA", "", "A#a");
        TraceNode nodeB = new TraceNode("node-b", "B", "B#b", "b()", "B.java", 20, "JAVA", "", "B#b");
        TraceLink link = new TraceLink("link-1", "node-a", "node-b",
                Instant.parse("2026-05-29T10:00:00Z"), TraceLinkKind.MANUAL);
        TraceDocument doc = new TraceDocument(
                3, "trace-delete", "Delete Test", "",
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(nodeA, nodeB),
                List.of(link),
                Set.of());
        storage.save("trace-delete.json", doc);
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.load("trace-delete.json");

        // When: delete node A
        controller.deleteNode("node-a");

        // Then: only node A is deleted, node B is preserved
        List<String> remaining = controller.state().currentDocument().nodes().stream()
                .map(TraceNode::id).toList();
        assertEquals(List.of("node-b"), remaining);
        // Link is preserved (not removed even though it references deleted node A)
        assertEquals(1, controller.state().currentDocument().links().size());
        assertEquals("node-a", controller.state().currentDocument().links().get(0).sourceNodeId());
        assertEquals("node-b", controller.state().currentDocument().links().get(0).targetNodeId());
    }

    @Test
    void survivingNodeIsStillLinkableAfterLinkedPartnerIsDeleted() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        TraceNode nodeA = new TraceNode("node-a", "A", "A#a", "a()", "A.java", 10, "JAVA", "", "A#a");
        TraceNode nodeB = new TraceNode("node-b", "B", "B#b", "b()", "B.java", 20, "JAVA", "", "B#b");
        TraceNode nodeC = new TraceNode("node-c", "C", "C#c", "c()", "C.java", 30, "JAVA", "", "C#c");
        TraceLink link = new TraceLink("link-1", "node-a", "node-b",
                Instant.parse("2026-05-29T10:00:00Z"), TraceLinkKind.MANUAL);
        TraceDocument doc = new TraceDocument(
                3, "trace-ghost", "Ghost Link Test", "",
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(nodeA, nodeB, nodeC),
                List.of(link),
                Set.of());
        storage.save("trace-ghost.json", doc);
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.load("trace-ghost.json");

        // Delete node-a; the preserved link now references a non-existent node
        controller.deleteNode("node-a");
        assertEquals(1, controller.state().currentDocument().links().size());

        // node-b should still be linkable — the dangling link must not block it
        controller.setPendingLinkSource("node-b");
        controller.linkPendingSourceTo("node-c", TraceLinkKind.MANUAL);

        // Two links total: the dangling one and the new node-b -> node-c
        assertEquals(2, controller.state().currentDocument().links().size());
    }

    @Test
    void cascadesDeleteToAllDescendants() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("tree.json", documentWithNestedNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.load("tree.json");

        controller.deleteNode("n1");
        // n1, n2 (child of n1), n3 (child of n2) should all be deleted
        List<String> remaining = controller.state().currentDocument().nodes().stream()
                .map(TraceNode::id).toList();
        assertEquals(List.of(), remaining);
    }

    @Test
    void deleteNode_cleansUpExpandedNodeIdsOfRemovedNodes() {
        TraceDocument doc = new TraceDocument(
                3, "tree-expanded", "Tree", "",
                Instant.parse("2026-06-02T10:00:00Z"),
                Instant.parse("2026-06-02T10:00:00Z"),
                List.of(
                        new TraceNode("n1", "root", "", "", "", 0, "", "", ""),
                        new TraceNode("n2", "child-a", "", "", "", 0, "", "", "", "n1", (String) null),
                        new TraceNode("n3", "child-b", "", "", "", 0, "", "", "", "n2", (String) null),
                        new TraceNode("n4", "unrelated", "", "", "", 0, "", "", "")),
                List.of(),
                Set.of("n1", "n2", "n3", "n4"));

        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("tree-expanded.json", doc);
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.load("tree-expanded.json");

        // Deleting n1 should cascade to n2 and n3, and clean up their expanded IDs
        controller.deleteNode("n1");

        Set<String> expanded = controller.state().currentDocument().expandedNodeIds();
        // n4 survives — its expanded ID should remain; n1/n2/n3 should be removed
        assertEquals(Set.of("n4"), expanded);
    }

    @Test
    void toggleNodeExpandAddsAndRemovesNodeId() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("expand-1.json", documentWithNestedNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.load("expand-1.json");

        controller.toggleNodeExpand("n1", true);
        assertTrue(controller.state().currentDocument().expandedNodeIds().contains("n1"));

        controller.toggleNodeExpand("n1", false);
        assertFalse(controller.state().currentDocument().expandedNodeIds().contains("n1"));
    }

    @Test
    void expandAllNodesExpandsAllParentNodes() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("expand-2.json", documentWithNestedNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.load("expand-2.json");

        controller.expandAllNodes();

        Set<String> expanded = controller.state().currentDocument().expandedNodeIds();
        assertTrue(expanded.contains("n1"));
        assertTrue(expanded.contains("n2"));
        // n3 has no children, should not be expanded
        assertFalse(expanded.contains("n3"));
    }

    @Test
    void collapseAllNodesClearsExpandedState() {
        TraceDocument doc = new TraceDocument(
                3, "expand-3", "Expand Test", "",
                Instant.parse("2026-06-02T10:00:00Z"),
                Instant.parse("2026-06-02T10:00:00Z"),
                List.of(
                        new TraceNode("n1", "root", "", "", "", 0, "", "", ""),
                        new TraceNode("n2", "child", "", "", "", 0, "", "", "", "n1", (String) null)),
                List.of(),
                Set.of("n1"));

        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("expand-4.json", doc);
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.load("expand-4.json");

        assertTrue(controller.state().currentDocument().expandedNodeIds().contains("n1"));

        controller.collapseAllNodes();

        assertTrue(controller.state().currentDocument().expandedNodeIds().isEmpty());
    }

    private static TraceDocument documentWithNestedNodes() {
        return new TraceDocument(
                3, "tree-1", "Tree", "",
                Instant.parse("2026-06-02T10:00:00Z"),
                Instant.parse("2026-06-02T10:00:00Z"),
                List.of(
                        new TraceNode("n1", "root", "", "", "", 0, "", "", ""),
                        new TraceNode("n2", "child-a", "", "", "", 0, "", "", "", "n1", (String) null),
                        new TraceNode("n3", "child-b", "", "", "", 0, "", "", "", "n2", (String) null)),
                List.of(), Set.of());
    }

    private static TraceDocument documentWithThreeNodes() {
        return new TraceDocument(
                3,
                "trace-1",
                "Trace 1",
                "note",
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(
                        new TraceNode("node-1", "line 1", "A#a", "a()", "A.java", 10, "JAVA", "", "A#a"),
                        new TraceNode("node-2", "line 2", "B#b", "b()", "B.java", 20, "JAVA", "", "B#b"),
                        new TraceNode("node-3", "line 3", "C#c", "c()", "C.java", 30, "JAVA", "", "C#c")),
                List.<TraceLink>of(),
                Set.of());
    }
}
