package com.geer.snowboard.v2.catalog.adapter.in.web;

import com.geer.snowboard.v2.bootstrap.ActorResolver;
import com.geer.snowboard.v2.catalog.application.port.in.CourseOperations;
import com.geer.snowboard.v2.catalog.application.port.in.CourseOperations.CreateCourse;
import com.geer.snowboard.v2.catalog.application.port.in.CourseOperations.UpdateCourse;
import com.geer.snowboard.v2.sharedkernel.Page;
import com.geer.snowboard.v2.catalog.application.port.in.CourseOperations.Course;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class CourseController {
    private final CourseOperations courses;
    private final ActorResolver actors;
    public CourseController(CourseOperations courses, ActorResolver actors) { this.courses = courses; this.actors = actors; }

    @GetMapping("/api/courses")
    public Page<Course> published(@RequestParam(required = false) Integer limit,
                                  @RequestParam(required = false) String cursor) {
        return courses.published(actors.current(), limit, cursor);
    }
    @GetMapping("/api/coach/courses")
    public Page<Course> coach(@RequestParam(required = false) Integer limit,
                              @RequestParam(required = false) String cursor) {
        return courses.coachCourses(actors.current(), limit, cursor);
    }
    @PostMapping("/api/coach/courses")
    public ResponseEntity<Course> create(@RequestHeader("Idempotency-Key") String key,
                                         @RequestBody CreateCourse body) {
        var result = courses.create(actors.current(), body, key);
        return ResponseEntity.status(result.created() ? 201 : 200).body(result.value());
    }
    @PatchMapping("/api/coach/courses/{id}")
    public Course update(@PathVariable String id, @RequestBody UpdateCourse body) {
        return courses.update(actors.current(), id, body);
    }
    @PostMapping("/api/coach/courses/{id}/archive")
    public Course archive(@PathVariable String id) {
        return courses.archive(actors.current(), id);
    }
}
