package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
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
}
