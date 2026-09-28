package com.geer.snowboard.v2.scheduling.application.port.in;

import java.time.Instant;

/** Local-only demonstration use case. It does not create availability or a booking. */
public interface CreateTimeWindowPreview {

    TimeWindowPreview create(Command command);

    record Command(Instant start, Instant end) {}
}
