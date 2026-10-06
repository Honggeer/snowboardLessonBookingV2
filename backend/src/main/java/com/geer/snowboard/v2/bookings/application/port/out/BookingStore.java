package com.geer.snowboard.v2.bookings.application.port.out;

import com.geer.snowboard.v2.bookings.application.port.in.BookingOperations.Booking;
import com.geer.snowboard.v2.sharedkernel.Page;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface BookingStore {
    Booking findById(String id);
    Booking lockById(String id);
    Booking findByKey(String studentId, String key);
    String fingerprint(String studentId, String key);
    Booking findByStudentSlot(String studentId, String slotId);
    boolean insert(Booking booking, String key, String fingerprint);
    Page<Booking> mine(String studentId, int limit, String cursor);
    Page<Booking> coach(String coachId, String status, int limit, String cursor);
    void lockStudent(String studentId);
    boolean confirmedOverlap(String studentId, Instant start, Instant end);
    boolean confirm(String id, Instant now);
    boolean reject(String id, String reason, Instant now);
    boolean cancel(String id, String reason, Instant now);
    boolean hasConfirmedDay(String coachId, LocalDate date);
    void rejectOtherPending(String slotId, String chosenId, Instant now);
    void rejectOtherMountains(String coachId, LocalDate date, String mountainId, Instant now);
    boolean hasPendingMountain(String mountainId);
    boolean hasPendingDay(String coachId, LocalDate date);
    List<Booking> lockUnplannedReminders(Instant now, int limit);
    boolean markReminderPlanned(String bookingId, Instant now);
}
