package com.geer.snowboard.v2.catalog.application.port.out;

import com.geer.snowboard.v2.catalog.application.port.in.CourseOperations.Course;
import com.geer.snowboard.v2.sharedkernel.Page;

public interface CourseStore {
    Course findById(String id);
    Course lockById(String id);
    Course findByKey(String coachId, String key);
    String fingerprint(String coachId, String key);
    boolean insert(Course course, String key, String fingerprint);
    Page<Course> list(String coachId, int limit, String cursor);
    void update(Course course);
    void archive(String id);
}
