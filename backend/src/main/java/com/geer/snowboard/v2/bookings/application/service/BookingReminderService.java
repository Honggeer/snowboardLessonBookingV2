package com.geer.snowboard.v2.bookings.application.service;

import com.geer.snowboard.v2.bookings.application.port.in.BookingOperations.Booking;
import com.geer.snowboard.v2.bookings.application.port.in.BookingReminderOperations;
import com.geer.snowboard.v2.bookings.application.port.out.BookingMailQueue;
import com.geer.snowboard.v2.bookings.application.port.out.BookingReminderSettings;
import com.geer.snowboard.v2.bookings.application.port.out.BookingStore;
import com.geer.snowboard.v2.bookings.domain.LessonReminderSchedule;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingReminderService implements BookingReminderOperations {
    private final BookingStore bookings;
    private final BookingMailQueue queue;
    private final BookingReminderSettings settings;
    private final Clock clock;
    public BookingReminderService(BookingStore bookings, BookingMailQueue queue,
                                  BookingReminderSettings settings, Clock clock) {
        this.bookings=bookings; this.queue=queue; this.settings=settings; this.clock=clock;
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public void scheduleConfirmed(String bookingId, Instant now) {
        if (settings.enabled()) plan(bookings.lockById(bookingId), now);
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public void backfill() {
        if (!settings.enabled()) return;
        Instant now=clock.instant();
        for (Booking booking : bookings.lockUnplannedReminders(now, 50)) plan(booking, now);
    }
    private void plan(Booking booking, Instant now) {
        if (booking==null || !"CONFIRMED".equals(booking.status()) || booking.decidedAt()==null) return;
        if (!bookings.markReminderPlanned(booking.id(), now)) return;
        if (!now.isBefore(booking.startAt().minusSeconds(86400))) return;
        LessonReminderSchedule.dueAt(booking.startAt(), booking.decidedAt()).ifPresent(due -> {
            queue.enqueueReminder(booking.id(), BookingMailQueue.STUDENT_LESSON_REMINDER,
                    booking.studentId(), due, booking.startAt(), now);
            queue.enqueueReminder(booking.id(), BookingMailQueue.COACH_LESSON_REMINDER,
                    booking.coachId(), due, booking.startAt(), now);
        });
    }
}
