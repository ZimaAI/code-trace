package com.zimaai.codetrace.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.model.TraceVersion;
import com.zimaai.codetrace.model.TraceVersionSource;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TraceJsonMapperTest {
    @Test
    void writesAndReadsCurrentAndHistoryVersions() throws Exception {
        TraceNode node = new TraceNode(
                "node-1",
                "login()",
                "com.example.AuthService.login",
                "login(String username)",
                "src/main/java/com/example/AuthService.java",
                42,
                "JAVA",
                "entry point",
                "com.example.AuthService#login(String)");
        TraceVersion current = new TraceVersion(
                "v1",
                TraceVersionSource.RECORDING,
                Instant.parse("2026-05-28T10:00:00Z"),
                Instant.parse("2026-05-28T10:00:00Z"),
                true,
                List.of(node));
        TraceDocument document = new TraceDocument(
                1,
                "trace-auth-login",
                "Auth Login",
                "trace note",
                Instant.parse("2026-05-28T09:00:00Z"),
                Instant.parse("2026-05-28T10:00:00Z"),
                current,
                List.of(current));

        TraceJsonMapper mapper = new TraceJsonMapper();
        String json = mapper.write(document);
        TraceDocument restored = mapper.read(json);

        assertTrue(json.contains("\"schemaVersion\" : 1"));
        assertEquals("Auth Login", restored.name());
        assertEquals("entry point", restored.current().nodes().get(0).note());
        assertEquals(1, restored.history().size());
    }
}
