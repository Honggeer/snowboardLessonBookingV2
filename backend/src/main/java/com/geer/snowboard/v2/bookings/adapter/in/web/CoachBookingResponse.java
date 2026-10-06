package com.geer.snowboard.v2.bookings.adapter.in.web;

import com.geer.snowboard.v2.bookings.application.port.in.BookingOperations.CoachBooking;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CoachBookingResponse(String id, String slotId, String courseId, String coachId, String studentId,
        String studentName, String status, String decisionReason, String courseTitle, BigDecimal priceAmount,
        String currency, String mountainId, String location, String zoneId, LocalDate localDate,
        Instant startAt, Instant endAt, Instant createdAt, Instant decidedAt, String studentPhone) {
    public static CoachBookingResponse from(CoachBooking result) {
        var b = result.booking();
        return new CoachBookingResponse(b.id(), b.slotId(), b.courseId(), b.coachId(), b.studentId(), b.studentName(),
                b.status(), b.decisionReason(), b.courseTitle(), b.priceAmount(), b.currency(), b.mountainId(), b.location(),
                b.zoneId(), b.localDate(), b.startAt(), b.endAt(), b.createdAt(), b.decidedAt(), result.studentPhone());
    }
}
