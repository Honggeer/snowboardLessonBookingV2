package com.geer.snowboard.v2.bookings.application.port.in;

import com.geer.snowboard.v2.sharedkernel.Actor;
import com.geer.snowboard.v2.sharedkernel.Page;
import java.time.Instant;
import java.time.LocalDate;

public interface LessonSummaryOperations {
    record Write(String practiceContent, String improvementAreas, String reinforcementExercises, Long expectedVersion) {}
    record Summary(String bookingId, String state, String practiceContent, String improvementAreas,
                   String reinforcementExercises, long version, Instant updatedAt, Instant publishedAt) {}
    record CompletedLesson(String id, String studentName, String courseTitle, String location, String zoneId,
                           LocalDate localDate, Instant startAt, Instant endAt, String summaryState) {}
    Page<CompletedLesson> completed(Actor actor, Integer limit, String cursor);
    Page<CompletedLesson> coachCompleted(Actor actor, Integer limit, String cursor);
    Summary coachSummary(Actor actor, String bookingId);
    Summary studentSummary(Actor actor, String bookingId);
    Summary save(Actor actor, String bookingId, Write command);
    Summary publish(Actor actor, String bookingId, Write command);
}
