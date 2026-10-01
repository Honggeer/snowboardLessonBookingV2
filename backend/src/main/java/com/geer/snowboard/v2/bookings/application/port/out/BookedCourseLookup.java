package com.geer.snowboard.v2.bookings.application.port.out;

import java.math.BigDecimal;

public interface BookedCourseLookup {
    record CourseSnapshot(String id, String coachId, String title, BigDecimal priceAmount, String currency) {}
    CourseSnapshot find(String courseId);
}
