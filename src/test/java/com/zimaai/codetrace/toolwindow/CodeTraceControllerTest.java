package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void linksMoveAndDeleteOperateOnWholePair() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-2.json", documentWithThreeNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);

        controller.load("trace-2.json");
        controller.setPendingLinkSource("node-1");
        controller.linkPendingSourceTo("node-2", TraceLinkKind.MANUAL);

        assertEquals(1, controller.state().currentDocument().links().size());

        int movedTo = controller.moveNodeOrPair("node-2", 1);
        assertEquals(2, movedTo);
        List<String> order = controller.state().currentDocument().nodes().stream()
                .map(TraceNode::id)
                .collect(Collectors.toList());
        assertEquals(List.of("node-3", "node-1", "node-2"), order);

        controller.deleteNodeOrPair("node-1");
        assertEquals(List.of("node-3"), controller.state().currentDocument().nodes().stream()
                .map(TraceNode::id)
                .collect(Collectors.toList()));
        assertTrue(controller.state().currentDocument().links().isEmpty());
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

        controller.moveNodeOrPairToIndex("node-2", 0);

        assertEquals(List.of("node-2", "node-3", "node-1"),
                controller.state().currentDocument().nodes().stream().map(TraceNode::id).toList());
    }

    private static TraceDocument linkedDocumentWithThreeNodes() {
        return new TraceDocument(
                2,
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
                        Instant.parse("2026-05-29T10:00:00Z"), TraceLinkKind.MANUAL)));
    }

    private static TraceDocument documentWithThreeNodes() {
        return new TraceDocument(
                2,
                "trace-1",
                "Trace 1",
                "note",
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(
                        new TraceNode("node-1", "line 1", "A#a", "a()", "A.java", 10, "JAVA", "", "A#a"),
                        new TraceNode("node-2", "line 2", "B#b", "b()", "B.java", 20, "JAVA", "", "B#b"),
                        new TraceNode("node-3", "line 3", "C#c", "c()", "C.java", 30, "JAVA", "", "C#c")),
                List.<TraceLink>of());
    }
}
