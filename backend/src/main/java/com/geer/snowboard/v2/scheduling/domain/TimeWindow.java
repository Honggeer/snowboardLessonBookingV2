package com.geer.snowboard.v2.scheduling.domain;

import java.time.Duration;
import java.time.Instant;

/** A half-open interval [start, end) used only to validate a scheduling preview. */
public final class TimeWindow {

    private final Instant start;
    private final Instant end;

    private TimeWindow(Instant start, Instant end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start and end are required");
        }
        this.start = start;
        this.end = end;
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end must be after start");
        }
    }

    public static TimeWindow of(Instant start, Instant end) {
        return new TimeWindow(start, end);
    }

    public Instant start() {
        return start;
    }

    public Instant end() {
        return end;
    }

    public Duration duration() {
        return Duration.between(start, end);
    }
}
