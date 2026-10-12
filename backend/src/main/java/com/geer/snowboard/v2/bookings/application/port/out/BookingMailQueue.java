package com.geer.snowboard.v2.bookings.application.port.out;

import java.time.Instant;

public interface BookingMailQueue {
    String COACH_SUMMARY_REMINDER = "COACH_SUMMARY_REMINDER";
    String STUDENT_SUMMARY_PUBLISHED = "STUDENT_SUMMARY_PUBLISHED";
    String APPLICATION_RECEIVED = "APPLICATION_RECEIVED";
    String BOOKING_CONFIRMED = "BOOKING_CONFIRMED";
    String BOOKING_REJECTED = "BOOKING_REJECTED";
    String STUDENT_LESSON_REMINDER = "STUDENT_LESSON_REMINDER";
    String COACH_LESSON_REMINDER = "COACH_LESSON_REMINDER";

    record Task(long id, String bookingId, String eventType, String recipientAccountId,
                int attempts, String claimToken, Instant reminderStartAt, Instant claimUntil, Instant summaryEndAt) {
        public boolean reminder() { return STUDENT_LESSON_REMINDER.equals(eventType) || COACH_LESSON_REMINDER.equals(eventType); }
    }

    void enqueue(String bookingId, String eventType, String recipientAccountId, Instant now);
    void enqueueReminder(String bookingId, String eventType, String recipientAccountId,
                         Instant dueAt, Instant startAt, Instant now);
    void enqueueSummaryReminder(String bookingId, String recipientAccountId, Instant endAt, Instant now);
    Task claim(Instant now, boolean includeReminders, boolean includeSummaryReminders);
    default Task claim(Instant now) { return claim(now, true); }
    Task claim(Instant now, boolean includeReminders);
    boolean ownsLease(Task task, Instant now);
    void sent(Task task, Instant now);
    void failed(Task task, Instant nextAttempt, String errorType, boolean finalFailure);
    void skipped(Task task);
    void cleanup(Instant cutoff);
}
