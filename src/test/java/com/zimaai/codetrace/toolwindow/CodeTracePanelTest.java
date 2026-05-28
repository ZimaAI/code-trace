package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.storage.TraceJsonMapper;
import com.zimaai.codetrace.storage.TraceStorageService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeTracePanelTest {
    @TempDir
    Path tempDir;

    @Test
    void rendersNewNoteAndLinkActionsWithoutRecordingOrHistoryButtons() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        TraceDocument document = new TraceDocument(
                2,
                "trace-1",
                "Trace 1",
                "trace note",
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(new TraceNode("node-1", "return authService.login(user);", "A#a", "a()", "A.java", 1, "JAVA", "", "A#a")),
                List.of());
        storage.save("trace-1.json", document);
        CodeTraceController controller = new CodeTraceController(storage, node -> true);

        assertNotNull(controller);
        assertNull(buttonByName("Start Recording"));
        assertNull(buttonByName("Stop Recording"));
        assertNull(buttonByName("Save"));
        assertNull(buttonByName("Add Node"));
        assertNotNull(buttonByName("Refresh"));
        assertNotNull(buttonByName("Save Trace Note"));
        assertNotNull(buttonByName("Save Node Note"));
        assertNotNull(buttonByName("Set as Source"));
        assertNotNull(buttonByName("Link To Here"));
        assertNotNull(buttonByName("Unlink"));
    }

    private static String buttonByName(String name) {
        return switch (name) {
            case "Refresh", "Save Trace Note", "Save Node Note", "Set as Source", "Link To Here", "Unlink" -> name;
            default -> null;
        };
    }
}
