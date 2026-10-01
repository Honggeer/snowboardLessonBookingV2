package com.geer.snowboard.v2.bookings.adapter.out.module;

import com.geer.snowboard.v2.bookings.application.port.out.BookedCourseLookup;
import com.geer.snowboard.v2.catalog.application.port.in.CourseOperations;
import org.springframework.stereotype.Component;

@Component
public final class BookingCourseAdapter implements BookedCourseLookup {
    private final CourseOperations courses;
    public BookingCourseAdapter(CourseOperations courses) { this.courses = courses; }
    @Override public CourseSnapshot find(String courseId) {
        var course = courses.lockPublished(courseId);
        return course == null ? null : new CourseSnapshot(course.id(), course.coachId(), course.title(),
                course.priceAmount(), course.currency());
    }
}
