package com.zimaai.codetrace.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class TraceJsonMapper {
    private final ObjectMapper mapper;

    public TraceJsonMapper() {
        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public String write(TraceDocument document) throws Exception {
        return mapper.writeValueAsString(document);
    }

    public TraceDocument read(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        int schemaVersion = root.path("schemaVersion").asInt(1);
        if (schemaVersion >= 3) {
            return mapper.treeToValue(root, TraceDocument.class);
        }
        if (schemaVersion >= 2) {
            return migrateSchemaTwo(root);
        }
        return migrateSchemaOne(root);
    }

    private TraceDocument migrateSchemaTwo(JsonNode root) throws Exception {
        TraceDocument doc = mapper.treeToValue(root, TraceDocument.class);
        List<TraceNode> migratedNodes = doc.nodes().stream()
                .map(n -> new TraceNode(
                        n.id(), n.displayName(), n.qualifiedName(), n.signature(),
                        n.filePath(), n.line(), n.language(), n.note(),
                        n.navigationHint(), null, null))
                .toList();
        return new TraceDocument(
                3,
                doc.id(),
                doc.name(),
                doc.description(),
                doc.createdAt(),
                doc.updatedAt(),
                migratedNodes,
                doc.links(),
                Set.of());
    }

    private TraceDocument migrateSchemaOne(JsonNode root) throws Exception {
        List<TraceNode> migratedNodes = new ArrayList<>();
        JsonNode currentNodes = root.path("current").path("nodes");
        if (currentNodes.isArray()) {
            for (JsonNode node : currentNodes) {
                migratedNodes.add(new TraceNode(
                        node.path("id").asText(),
                        node.path("displayName").asText(),
                        node.path("qualifiedName").asText(),
                        node.path("signature").asText(),
                        node.path("filePath").asText(),
                        node.path("line").asInt(),
                        node.path("language").asText(),
                        node.path("note").asText(""),
                        node.path("navigationHint").asText(""),
                        null,
                        null));
            }
        }
        return new TraceDocument(
                3,
                root.path("id").asText(),
                root.path("name").asText(),
                root.path("description").asText(""),
                parseInstant(root.path("createdAt")),
                parseInstant(root.path("updatedAt")),
                List.copyOf(migratedNodes),
                List.<TraceLink>of(),
                Set.of());
    }

    private Instant parseInstant(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return Instant.now();
        }
        try {
            return mapper.convertValue(value, Instant.class);
        } catch (IllegalArgumentException exception) {
            return Instant.now();
        }
    }
}
