package com.zimaai.codetrace.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceVersion;
import com.zimaai.codetrace.model.TraceVersionSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TraceStorageServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void createsLoadsCopiesRenamesAndDeletesTraceFiles() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        TraceDocument document = new TraceDocument(
                1, "trace-1", "Trace 1", "note",
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

        storage.save("trace-1.json", document);
        assertTrue(Files.exists(tempDir.resolve("code-trace").resolve("trace-1.json")));
        assertEquals(1, storage.listFiles().size());

        storage.copy("trace-1.json", "trace-1-copy.json");
        storage.rename("trace-1-copy.json", "trace-1-renamed.json");
        storage.delete("trace-1-renamed.json");

        assertEquals(List.of("trace-1.json"), storage.listFiles());
        assertEquals("Trace 1", storage.load("trace-1.json").name());
    }
}
