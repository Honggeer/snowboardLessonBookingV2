package com.geer.snowboard.v2.catalog.application.port.in;

import com.geer.snowboard.v2.sharedkernel.Actor;
import com.geer.snowboard.v2.sharedkernel.Creation;
import com.geer.snowboard.v2.sharedkernel.Page;
import java.math.BigDecimal;

public interface CourseOperations {
    record Course(String id, String coachId, String title, String description,
                  BigDecimal priceAmount, String currency, boolean active) {}
    record CreateCourse(String title, String description, String priceAmount, String currency) {}
    record UpdateCourse(String title, String description, String priceAmount, String currency) {}

    Creation<Course> create(Actor actor, CreateCourse command, String idempotencyKey);
    Page<Course> published(Actor actor, Integer limit, String cursor);
    Page<Course> coachCourses(Actor actor, Integer limit, String cursor);
    Course findPublished(String id);
    Course lockPublished(String id);
    Course update(Actor actor, String id, UpdateCourse command);
    Course archive(Actor actor, String id);
}
