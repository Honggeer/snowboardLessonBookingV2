package com.geer.snowboard.v2.bookings.domain;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
public final class LessonReminderSchedule {
    private LessonReminderSchedule() {}
    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");
    public static Optional<Instant> dueAt(Instant startAt, Instant confirmedAt) {
        if (confirmedAt == null) return Optional.empty();
        Instant first = startAt.minus(Duration.ofHours(48));
        Instant last = startAt.minus(Duration.ofHours(24));
        var endDate = last.atZone(TORONTO).toLocalDate();
        for (var date = first.atZone(TORONTO).toLocalDate(); !date.isAfter(endDate); date = date.plusDays(1)) {
            Instant run = date.atTime(18, 0).atZone(TORONTO).toInstant();
            if (!run.isBefore(first) && run.isBefore(last) && !run.isBefore(confirmedAt))
                return Optional.of(run);
        }
        return Optional.empty();
    }
}
