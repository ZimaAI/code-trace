package com.zimaai.codetrace.storage;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zimaai.codetrace.model.TraceDocument;

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
        return mapper.readValue(json, TraceDocument.class);
    }
}
