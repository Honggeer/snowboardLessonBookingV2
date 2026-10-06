package com.geer.snowboard.v2.bookings.application.port.in;

import java.time.Instant;

/** Internal jobs and the already-authorized confirmation use case; no HTTP entry point. */
public interface BookingReminderOperations {
    void scheduleConfirmed(String bookingId, Instant now);
    void backfill();
}
