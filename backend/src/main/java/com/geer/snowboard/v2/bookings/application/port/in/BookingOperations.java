package com.geer.snowboard.v2.bookings.application.port.in;

import com.geer.snowboard.v2.sharedkernel.Actor;
import com.geer.snowboard.v2.sharedkernel.Creation;
import com.geer.snowboard.v2.sharedkernel.Page;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface BookingOperations {
    record Booking(String id, String slotId, String courseId, String coachId, String studentId,
                   String studentName, String status, String decisionReason, String courseTitle,
                   BigDecimal priceAmount, String currency, String mountainId, String location, String zoneId,
                   LocalDate localDate, Instant startAt, Instant endAt, Instant createdAt, Instant decidedAt) {}
    record Apply(String courseId, String slotId, String mountainId) {}
    record Reject(String reason) {}
    record Cancel(String reason) {}
    record MountainResult(String id, String name, boolean active) {}
    record AvailabilityDay(String localDate, String startTime, String endTime, String mountainId) {}
    record ReplacementRequest(List<AvailabilityDay> days) {}
    record AvailabilitySlot(String id, LocalDate localDate, Instant startAt, Instant endAt, String status) {}
    record AvailabilityTail(String localDate, String startTime, String endTime) {}
    record AvailabilityBatch(List<AvailabilitySlot> slots, List<AvailabilityTail> tails) {}

    Creation<Booking> apply(Actor actor, Apply command, String idempotencyKey);
    Page<Booking> mine(Actor actor, Integer limit, String cursor);
    Page<Booking> coach(Actor actor, String status, Integer limit, String cursor);
    Booking confirm(Actor actor, String bookingId);
    Booking reject(Actor actor, String bookingId, Reject command);
    Booking cancel(Actor actor, String bookingId, Cancel command);
    MountainResult deactivateMountain(Actor actor, String mountainId);
    Creation<AvailabilityBatch> replaceAvailability(Actor actor, ReplacementRequest command, String idempotencyKey);
}
