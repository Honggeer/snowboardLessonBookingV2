package com.geer.snowboard.v2.bookings.application.service;

import com.geer.snowboard.v2.bookings.application.port.in.LessonSummaryReminderOperations;
import com.geer.snowboard.v2.bookings.application.port.in.BookingOperations.Booking;
import com.geer.snowboard.v2.bookings.application.port.out.*;
import com.geer.snowboard.v2.bookings.domain.LessonSummary;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LessonSummaryReminderService implements LessonSummaryReminderOperations {
    private final BookingStore bookings;
    private final LessonSummaryStore summaries;
    private final BookingMailQueue queue;
    private final LessonSummarySettings settings;
    private final Clock clock;
    public LessonSummaryReminderService(BookingStore bookings,LessonSummaryStore summaries,BookingMailQueue queue,
                                        LessonSummarySettings settings,Clock clock) {
        this.bookings=bookings; this.summaries=summaries; this.queue=queue; this.settings=settings; this.clock=clock;
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public void scheduleConfirmed(String bookingId,Instant now) {
        Booking booking=bookings.lockById(bookingId);
        if(booking==null || !"CONFIRMED".equals(booking.status())) return;
        // Record late confirmations even while paused, so a later backfill cannot invent a reminder.
        if(!booking.endAt().isAfter(now)) { bookings.markSummaryReminderPlanned(bookingId,now); return; }
        if(settings.remindersEnabled()) plan(booking,now);
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public void backfill() {
        if(!settings.remindersEnabled()) return;
        Instant now=clock.instant();
        for(Booking booking:bookings.lockUnplannedSummaryReminders(50)) plan(booking,now);
    }
    private void plan(Booking booking,Instant now) {
        if(booking==null || !"CONFIRMED".equals(booking.status())) return;
        if(!bookings.markSummaryReminderPlanned(booking.id(),now)) return;
        LessonSummary summary=summaries.find(booking.id());
        if(summary!=null && summary.state()==LessonSummary.State.PUBLISHED) return;
        queue.enqueueSummaryReminder(booking.id(),booking.coachId(),booking.endAt(),now);
    }
}
