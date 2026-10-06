package com.geer.snowboard.v2.bookings;

import static org.assertj.core.api.Assertions.*;
import com.geer.snowboard.v2.bookings.domain.LessonReminderSchedule;
import java.time.*;
import org.junit.jupiter.api.Test;

class LessonReminderScheduleTest {
    private static final ZoneId TORONTO=ZoneId.of("America/Toronto");
    private static Instant at(String local) { return LocalDateTime.parse(local).atZone(TORONTO).toInstant(); }
    @Test void morningLessonRemindsTwoEveningsBeforeAndEveningLessonThePreviousEvening() {
        assertThat(LessonReminderSchedule.dueAt(at("2026-10-08T10:00"),at("2026-10-01T12:00")))
                .contains(at("2026-10-06T18:00"));
        assertThat(LessonReminderSchedule.dueAt(at("2026-10-07T20:00"),at("2026-10-01T12:00")))
                .contains(at("2026-10-06T18:00"));
    }
    @Test void lower24HourBoundaryIsExcludedAndUpper48HourBoundaryIsIncluded() {
        assertThat(LessonReminderSchedule.dueAt(at("2026-10-07T18:00"),at("2026-10-01T12:00")))
                .contains(at("2026-10-05T18:00"));
        assertThat(LessonReminderSchedule.dueAt(at("2026-10-07T18:00"),at("2026-10-05T18:00").plusNanos(1)))
                .isEmpty();
    }
    @Test void confirmationAtTheRunIsEligibleButAfterTheLastRunIsNot() {
        Instant run=at("2026-10-06T18:00"), start=at("2026-10-07T20:00");
        assertThat(LessonReminderSchedule.dueAt(start,run)).contains(run);
        assertThat(LessonReminderSchedule.dueAt(start,run.plusNanos(1))).isEmpty();
        assertThat(LessonReminderSchedule.dueAt(start,null)).isEmpty();
    }
    @Test void springForwardChoosesTheFirstOfTwoEligibleLocalEvenings() {
        Instant start=at("2027-03-15T18:30"), early=at("2027-03-10T12:00");
        assertThat(LessonReminderSchedule.dueAt(start,early)).contains(at("2027-03-13T18:00"));
        assertThat(LessonReminderSchedule.dueAt(start,at("2027-03-13T18:01")))
                .contains(at("2027-03-14T18:00"));
        assertThat(Duration.between(at("2027-03-13T18:00"),at("2027-03-14T18:00")))
                .isEqualTo(Duration.ofHours(23));
    }
    @Test void fallBackPreservesTheActual48HourWindowRatherThanInventingARun() {
        assertThat(LessonReminderSchedule.dueAt(at("2026-11-02T17:30"),at("2026-10-28T12:00"))).isEmpty();
        assertThat(LessonReminderSchedule.dueAt(at("2026-11-02T18:30"),at("2026-10-28T12:00")))
                .contains(at("2026-11-01T18:00"));
    }
}
