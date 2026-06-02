package com.zimaai.codetrace.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TraceJsonMapperTest {
    @Test
    void migratesSchemaOneCurrentNodesIntoSchemaTwoDocument() throws Exception {
        String legacyJson = """
                {
                  "schemaVersion": 1,
                  "id": "trace-auth-login",
                  "name": "Auth Login",
                  "description": "legacy note",
                  "createdAt": "2026-05-28T09:00:00Z",
                  "updatedAt": "2026-05-28T10:00:00Z",
                  "current": {
                    "versionId": "v1",
                    "source": "MANUAL",
                    "recordedAt": "2026-05-28T10:00:00Z",
                    "updatedAt": "2026-05-28T10:00:00Z",
                    "nodeDedupEnabled": true,
                    "nodes": [
                      {
                        "id": "node-1",
                        "displayName": "return authService.login(user);",
                        "qualifiedName": "AuthController#login",
                        "signature": "login(User user)",
                        "filePath": "src/AuthController.java",
                        "line": 21,
                        "language": "JAVA",
                        "note": "legacy",
                        "navigationHint": "AuthController#login(User)"
                      }
                    ]
                  },
                  "history": []
                }
                """;

        TraceDocument restored = new TraceJsonMapper().read(legacyJson);

        assertEquals(3, restored.schemaVersion());
        assertEquals("Auth Login", restored.name());
        assertEquals(1, restored.nodes().size());
        assertEquals("return authService.login(user);", restored.nodes().get(0).displayName());
        assertEquals(List.of(), restored.links());
    }

    @Test
    void writesAndReadsSchemaTwoNodesAndLinks() throws Exception {
        TraceNode source = new TraceNode(
                "node-1",
                "return authService.login(user);",
                "AuthController#login",
                "login(User user)",
                "src/AuthController.java",
                21,
                "JAVA",
                "source note",
                "AuthController#login(User)");
        TraceNode target = new TraceNode(
                "node-2",
                "public User login(User user) {",
                "AuthService#login",
                "login(User user)",
                "src/AuthService.java",
                14,
                "JAVA",
                "target note",
                "AuthService#login(User)");
        TraceDocument document = new TraceDocument(
                3,
                "trace-auth-login",
                "Auth Login",
                "trace note",
                Instant.parse("2026-05-29T09:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(source, target),
                List.of(new TraceLink(
                        "link-1",
                        "node-1",
                        "node-2",
                        Instant.parse("2026-05-29T10:00:00Z"),
                        TraceLinkKind.DETECTED)),
                java.util.Set.of());

        TraceJsonMapper mapper = new TraceJsonMapper();
        String json = mapper.write(document);
        TraceDocument restored = mapper.read(json);

        assertTrue(json.contains("\"schemaVersion\" : 3"));
        assertEquals("Auth Login", restored.name());
        assertEquals(2, restored.nodes().size());
        assertEquals("source note", restored.nodes().get(0).note());
        assertEquals(1, restored.links().size());
        assertEquals(TraceLinkKind.DETECTED, restored.links().get(0).kind());
    }

    @Test
    void preservesAbsoluteFilePathWhenReadingAndWritingSchemaTwoDocument() throws Exception {
        String absolutePath = Path.of("C:\\workspace\\code-trace\\src\\AuthController.java")
                .toString()
                .replace('\\', '/');
        TraceNode node = new TraceNode(
                "node-1",
                "return authService.login(user);",
                "AuthController#login",
                "login(User user)",
                absolutePath,
                21,
                "JAVA",
                "note",
                "AuthController#login(User)");
        TraceDocument document = new TraceDocument(
                3,
                "trace-auth-login",
                "Auth Login",
                "trace note",
                Instant.parse("2026-05-29T09:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(node),
                List.of(),
                java.util.Set.of());

        TraceJsonMapper mapper = new TraceJsonMapper();
        String json = mapper.write(document);
        TraceDocument restored = mapper.read(json);

        assertTrue(json.contains(absolutePath));
        assertEquals(absolutePath, restored.nodes().get(0).filePath());
    }

    @Test
    void migratesSchemaV2ToV3WithNullParentsAndEmptyExpandState() throws Exception {
        TraceJsonMapper mapper = new TraceJsonMapper();
        String v2Json = """
                {
                  "schemaVersion": 2,
                  "id": "trace-1",
                  "name": "Test",
                  "description": "desc",
                  "createdAt": "2026-06-02T10:00:00Z",
                  "updatedAt": "2026-06-02T10:00:00Z",
                  "nodes": [
                    {
                      "id": "node-1",
                      "displayName": "line 1",
                      "qualifiedName": "A#a",
                      "signature": "a()",
                      "filePath": "A.java",
                      "line": 10,
                      "language": "JAVA",
                      "note": "",
                      "navigationHint": "A#a"
                    }
                  ],
                  "links": []
                }
                """;
        TraceDocument doc = mapper.read(v2Json);
        assertEquals(3, doc.schemaVersion());
        assertEquals(1, doc.nodes().size());
        assertNull(doc.nodes().get(0).parentId());
        assertNull(doc.nodes().get(0).title());
        assertTrue(doc.expandedNodeIds().isEmpty());
    }
}
