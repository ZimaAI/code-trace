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
        if (schemaVersion >= 2) {
            return mapper.treeToValue(root, TraceDocument.class);
        }
        return migrateSchemaOne(root);
    }

    private TraceDocument migrateSchemaOne(JsonNode root) throws Exception {
        List<TraceNode> migratedNodes = new ArrayList<>();
        JsonNode currentNodes = root.path("current").path("nodes");
        if (currentNodes.isArray()) {
            for (JsonNode node : currentNodes) {
                migratedNodes.add(mapper.treeToValue(node, TraceNode.class));
            }
        }
        return new TraceDocument(
                2,
                root.path("id").asText(),
                root.path("name").asText(),
                root.path("description").asText(""),
                parseInstant(root.path("createdAt")),
                parseInstant(root.path("updatedAt")),
                List.copyOf(migratedNodes),
                List.<TraceLink>of());
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
