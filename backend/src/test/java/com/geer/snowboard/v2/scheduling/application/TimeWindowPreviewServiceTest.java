package com.geer.snowboard.v2.scheduling.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.geer.snowboard.v2.scheduling.adapter.out.memory.InMemoryTimeWindowPreviewStore;
import com.geer.snowboard.v2.scheduling.application.port.in.CreateTimeWindowPreview;
import com.geer.snowboard.v2.scheduling.application.port.in.TimeWindowPreview;
import com.geer.snowboard.v2.scheduling.application.service.TimeWindowPreviewService;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TimeWindowPreviewServiceTest {

    @Test
    void createsAndReadsAPreviewWithoutBookingAnAvailableTime() {
        var store = new InMemoryTimeWindowPreviewStore();
        var service = new TimeWindowPreviewService(store);
        var start = Instant.parse("2026-12-01T14:00:00Z");
        var end = Instant.parse("2026-12-01T15:30:00Z");

        TimeWindowPreview created = service.create(new CreateTimeWindowPreview.Command(start, end));

        assertEquals(start, created.start());
        assertEquals(end, created.end());
        assertEquals(5400, created.durationSeconds());
        assertEquals(created, service.find(created.id()).orElseThrow());
    }

    @Test
    void invalidWindowIsNotStored() {
        var store = new InMemoryTimeWindowPreviewStore();
        var service = new TimeWindowPreviewService(store);
        var instant = Instant.parse("2026-12-01T14:00:00Z");

        assertThrows(IllegalArgumentException.class,
                () -> service.create(new CreateTimeWindowPreview.Command(instant, instant)));
        assertEquals(0, store.size());
        assertFalse(service.find("missing").isPresent());
    }
}
