package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
