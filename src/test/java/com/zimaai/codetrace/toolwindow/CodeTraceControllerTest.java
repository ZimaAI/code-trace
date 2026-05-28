package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.model.TraceVersion;
import com.zimaai.codetrace.model.TraceVersionSource;
import com.zimaai.codetrace.recording.TraceRecordingService;
import com.zimaai.codetrace.storage.TraceJsonMapper;
import com.zimaai.codetrace.storage.TraceStorageService;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeTraceControllerTest {
    @TempDir
    Path tempDir;

    @Test
    void refreshPromptsWhenDocumentIsDirty() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-1.json", document("note"));
        CodeTraceController controller = new CodeTraceController(
                storage,
                decision -> decision == UnsavedChangesDecision.DISCARD,
                new TraceRecordingService(Clock.fixed(Instant.parse("2026-05-28T10:30:00Z"), ZoneOffset.UTC)),
                node -> true);

        controller.load("trace-1.json");
        controller.updateDescription("changed");
        boolean refreshed = controller.refreshCurrentFile();

        assertTrue(refreshed);
        assertEquals("note", controller.state().currentDocument().description());
        assertTrue(controller.state().dirtyHistory().contains(UnsavedChangesDecision.DISCARD));
    }

    @Test
    void updatesNodeNoteInCurrentVersion() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-2.json", documentWithNode("old-note"));
        CodeTraceController controller = new CodeTraceController(
                storage,
                decision -> true,
                new TraceRecordingService(Clock.fixed(Instant.parse("2026-05-28T10:30:00Z"), ZoneOffset.UTC)),
                node -> true);

        controller.load("trace-2.json");
        controller.updateNodeNote(0, "new-note");

        assertEquals("new-note", controller.state().currentDocument().current().nodes().get(0).note());
        assertTrue(controller.state().dirty());
    }

    @Test
    void supportsNodeCrudAndReorderInCurrentVersion() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-3.json", documentWithTwoNodes());
        CodeTraceController controller = new CodeTraceController(
                storage,
                decision -> true,
                new TraceRecordingService(Clock.fixed(Instant.parse("2026-05-28T10:30:00Z"), ZoneOffset.UTC)),
                node -> true);

        controller.load("trace-3.json");

        int inserted = controller.addNode(new TraceNode(
                "n3", "C()", "C#c", "c()", "C.java", 30, "JAVA", "note-c", "C#c"));
        assertEquals(2, inserted);
        assertEquals(3, controller.state().currentDocument().current().nodes().size());

        controller.updateNode(1, new TraceNode(
                "n2", "B-updated()", "B#b", "b()", "B.java", 22, "JAVA", "note-b2", "B#b"));
        assertEquals("B-updated()", controller.state().currentDocument().current().nodes().get(1).displayName());

        int movedTo = controller.moveNode(2, -1);
        assertEquals(1, movedTo);
        List<String> names = controller.state().currentDocument().current().nodes().stream()
                .map(TraceNode::displayName)
                .collect(Collectors.toList());
        assertEquals(List.of("A()", "C()", "B-updated()"), names);

        controller.deleteNode(1);
        assertEquals(2, controller.state().currentDocument().current().nodes().size());
        assertEquals("B-updated()", controller.state().currentDocument().current().nodes().get(1).displayName());
        assertTrue(controller.state().dirty());
    }

    private static TraceDocument document(String description) {
        return new TraceDocument(
                1, "trace-1", "Trace 1", description,
                Instant.parse("2026-05-28T10:00:00Z"),
                Instant.parse("2026-05-28T10:00:00Z"),
                new TraceVersion(
                        "v1",
                        TraceVersionSource.MANUAL,
                        Instant.parse("2026-05-28T10:00:00Z"),
                        Instant.parse("2026-05-28T10:00:00Z"),
                        true,
                        List.of()),
                List.of());
    }

    private static TraceDocument documentWithNode(String note) {
        TraceNode node = new TraceNode(
                "n1",
                "login()",
                "AuthService#login",
                "login()",
                "AuthService.java",
                12,
                "JAVA",
                note,
                "AuthService#login()");
        return new TraceDocument(
                1, "trace-2", "Trace 2", "description",
                Instant.parse("2026-05-28T10:00:00Z"),
                Instant.parse("2026-05-28T10:00:00Z"),
                new TraceVersion(
                        "v2",
                        TraceVersionSource.MANUAL,
                        Instant.parse("2026-05-28T10:00:00Z"),
                        Instant.parse("2026-05-28T10:00:00Z"),
                        true,
                        List.of(node)),
                List.of());
    }

    private static TraceDocument documentWithTwoNodes() {
        TraceNode nodeA = new TraceNode(
                "n1", "A()", "A#a", "a()", "A.java", 10, "JAVA", "note-a", "A#a");
        TraceNode nodeB = new TraceNode(
                "n2", "B()", "B#b", "b()", "B.java", 20, "JAVA", "note-b", "B#b");
        return new TraceDocument(
                1, "trace-3", "Trace 3", "description",
                Instant.parse("2026-05-28T10:00:00Z"),
                Instant.parse("2026-05-28T10:00:00Z"),
                new TraceVersion(
                        "v3",
                        TraceVersionSource.MANUAL,
                        Instant.parse("2026-05-28T10:00:00Z"),
                        Instant.parse("2026-05-28T10:00:00Z"),
                        true,
                        List.of(nodeA, nodeB)),
                List.of());
    }
}
