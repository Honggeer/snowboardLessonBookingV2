package com.geer.snowboard.v2.bookings.application.port.out;

import java.time.Instant;

public interface BookingMailQueue {
    String APPLICATION_RECEIVED = "APPLICATION_RECEIVED";
    String BOOKING_CONFIRMED = "BOOKING_CONFIRMED";

    record Task(long id, String bookingId, String eventType, String recipientAccountId,
                int attempts, String claimToken) {}

    void enqueue(String bookingId, String eventType, String recipientAccountId, Instant now);
    Task claim(Instant now);
    void sent(Task task, Instant now);
    void failed(Task task, Instant nextAttempt, String errorType, boolean finalFailure);
    void skipped(Task task);
    void cleanup(Instant cutoff);
}
