package com.zimaai.codetrace.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
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
        TraceNode node = new TraceNode(
                "node-1",
                "return authService.login(user);",
                "AuthController#login",
                "login(User user)",
                "src/AuthController.java",
                21,
                "JAVA",
                "",
                "AuthController#login(User)");
        TraceDocument document = new TraceDocument(
                2, "trace-1", "Trace 1", "note",
                Instant.parse("2026-05-28T10:00:00Z"),
                Instant.parse("2026-05-28T10:00:00Z"),
                List.of(node),
                List.of());

        storage.save("trace-1.json", document);
        assertTrue(Files.exists(tempDir.resolve("code-trace").resolve("trace-1.json")));
        assertEquals(1, storage.listFiles().size());

        storage.copy("trace-1.json", "trace-1-copy.json");
        storage.rename("trace-1-copy.json", "trace-1-renamed.json");
        storage.delete("trace-1-renamed.json");

        assertEquals(List.of("trace-1.json"), storage.listFiles());
        assertEquals("Trace 1", storage.load("trace-1.json").name());
        assertEquals(1, storage.load("trace-1.json").nodes().size());
    }
}
