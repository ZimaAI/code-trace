package com.zimaai.codetrace.recording;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceVersion;
import com.zimaai.codetrace.model.TraceVersionSource;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class TraceRecordingService {
    private final Clock clock;
    private TraceRecordingSession session;
    private boolean dedupEnabled;

    public TraceRecordingService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public void start(boolean suppressConsecutiveDuplicates) {
        this.dedupEnabled = suppressConsecutiveDuplicates;
        this.session = new TraceRecordingSession(suppressConsecutiveDuplicates);
    }

    public boolean isRecording() {
        return session != null;
    }

    public void record(TraceableNavigationTarget target) {
        if (session != null && target != null) {
            session.record(target);
        }
    }

    public TraceDocument stop(TraceDocument document) {
        if (document == null) {
            session = null;
            throw new IllegalStateException("No trace document is selected");
        }
        if (session == null) {
            return document;
        }
        Instant now = clock.instant();
        TraceVersion nextVersion = new TraceVersion(
                "v-" + UUID.randomUUID(),
                TraceVersionSource.RECORDING,
                now,
                now,
                dedupEnabled,
                session.snapshot());
        List<TraceVersion> history = new ArrayList<>(document.history());
        if (document.current() != null) {
            history.add(document.current());
        }
        session = null;
        return new TraceDocument(
                document.schemaVersion(),
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                nextVersion,
                List.copyOf(history));
    }
}
