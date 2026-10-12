package com.geer.snowboard.v2.bookings.application.port.out;

import com.geer.snowboard.v2.bookings.domain.LessonSummary;
import com.geer.snowboard.v2.bookings.application.port.in.LessonSummaryOperations.CompletedLesson;
import com.geer.snowboard.v2.sharedkernel.Page;
import java.time.Instant;

public interface LessonSummaryStore {
    LessonSummary find(String bookingId);
    void save(LessonSummary summary);
    Page<CompletedLesson> completedForStudent(String studentId, Instant now, int limit, String cursor);
    Page<CompletedLesson> completedForCoach(String coachId, Instant now, int limit, String cursor);
}
