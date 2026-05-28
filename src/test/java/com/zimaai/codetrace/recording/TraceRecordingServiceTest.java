package com.zimaai.codetrace.recording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceVersion;
import com.zimaai.codetrace.model.TraceVersionSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class TraceRecordingServiceTest {
    @Test
    void archivesPreviousCurrentAndSuppressesConsecutiveDuplicates() {
        TraceRecordingService service = new TraceRecordingService(
                Clock.fixed(Instant.parse("2026-05-28T10:30:00Z"), ZoneOffset.UTC));
        TraceDocument original = new TraceDocument(
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

        service.start(true);
        service.record(new TraceableNavigationTarget("a", "A", "A#a()", "a()", "A.java", 1, "JAVA", "A#a()"));
        service.record(new TraceableNavigationTarget("a", "A", "A#a()", "a()", "A.java", 1, "JAVA", "A#a()"));
        service.record(new TraceableNavigationTarget("b", "B", "B#b()", "b()", "B.java", 2, "JAVA", "B#b()"));

        TraceDocument updated = service.stop(original);

        assertEquals(2, updated.current().nodes().size());
        assertEquals("A", updated.current().nodes().get(0).displayName());
        assertEquals(1, updated.history().size());
        assertSame(original.current(), updated.history().get(0));
        assertEquals(TraceVersionSource.RECORDING, updated.current().source());
    }
}
