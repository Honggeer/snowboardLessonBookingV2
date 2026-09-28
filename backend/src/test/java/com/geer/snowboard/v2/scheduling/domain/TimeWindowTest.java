package com.geer.snowboard.v2.scheduling.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TimeWindowTest {

    private static final Instant START = Instant.parse("2026-12-01T14:00:00Z");

    @Test
    void calculatesDurationForAValidWindow() {
        TimeWindow window = TimeWindow.of(START, START.plus(Duration.ofMinutes(90)));

        assertEquals(Duration.ofMinutes(90), window.duration());
    }

    @Test
    void rejectsEqualOrReversedEndpoints() {
        assertThrows(IllegalArgumentException.class, () -> TimeWindow.of(START, START));
        assertThrows(IllegalArgumentException.class,
                () -> TimeWindow.of(START, START.minus(Duration.ofSeconds(1))));
    }

    @Test
    void rejectsMissingEndpoints() {
        assertThrows(IllegalArgumentException.class, () -> TimeWindow.of(null, START));
        assertThrows(IllegalArgumentException.class, () -> TimeWindow.of(START, null));
    }
}
