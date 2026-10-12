package com.geer.snowboard.v2.bookings.application.port.in;

import java.time.Instant;

public interface LessonSummaryReminderOperations {
    void scheduleConfirmed(String bookingId, Instant now);
    void backfill();
}
