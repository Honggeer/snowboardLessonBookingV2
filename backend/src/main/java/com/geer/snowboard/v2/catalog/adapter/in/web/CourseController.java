package com.geer.snowboard.v2.catalog.adapter.in.web;

import com.geer.snowboard.v2.bootstrap.ActorResolver;
import com.geer.snowboard.v2.catalog.application.port.in.CourseOperations;
import com.geer.snowboard.v2.catalog.application.port.in.CourseOperations.*;
import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import com.geer.snowboard.v2.sharedkernel.Page;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

@RestController
public final class CourseController {
    // JSON presence and coercion belong to the Web boundary; signed URLs are display data.
    public record CoverResponse(String id,String url,Instant expiresAt,int width,int height) {}
    public record CourseResponse(String id,String coachId,String title,String description,BigDecimal priceAmount,
            String currency,boolean active,String coverAssetId,BigDecimal coverPositionX,BigDecimal coverPositionY,CoverResponse cover) {}
    private final CourseOperations courses;
    private final ActorResolver actors;
    public CourseController(CourseOperations courses, ActorResolver actors) { this.courses = courses; this.actors = actors; }
    private CourseResponse response(Course c) {
        Cover cover=courses.cover(c);
        return new CourseResponse(c.id(),c.coachId(),c.title(),c.description(),c.priceAmount(),c.currency(),c.active(),
                c.coverAssetId(),c.coverPositionX(),c.coverPositionY(),cover==null?null:new CoverResponse(cover.id(),cover.url(),cover.expiresAt(),cover.width(),cover.height()));
    }
    private ResponseEntity<Page<CourseResponse>> page(Page<Course> page) {
        return ResponseEntity.ok().header("Cache-Control","no-store")
                .body(new Page<>(page.items().stream().map(this::response).toList(),page.nextCursor()));
    }
    @GetMapping("/api/courses")
    public ResponseEntity<Page<CourseResponse>> published(@RequestParam(required = false) Integer limit,
                                  @RequestParam(required = false) String cursor) {
        return page(courses.published(actors.current(), limit, cursor));
    }
    @GetMapping("/api/coach/courses")
    public ResponseEntity<Page<CourseResponse>> coach(@RequestParam(required = false) Integer limit,
                              @RequestParam(required = false) String cursor) {
        return page(courses.coachCourses(actors.current(), limit, cursor));
    }
    @PostMapping("/api/coach/courses")
    public ResponseEntity<CourseResponse> create(@RequestHeader("Idempotency-Key") String key,@RequestBody JsonNode body) {
        Set<String> fields=fields(body);
        var result=courses.create(actors.current(),new CreateCourse(text(body,"title"),text(body,"description"),
                text(body,"priceAmount"),text(body,"currency"),text(body,"coverAssetId"),number(body,"coverPositionX"),number(body,"coverPositionY"),fields),key);
        return ResponseEntity.status(result.created()?201:200).header("Cache-Control","no-store").body(response(result.value()));
    }
    @PatchMapping("/api/coach/courses/{id}")
    public ResponseEntity<CourseResponse> update(@PathVariable String id,@RequestBody JsonNode body) {
        Set<String> fields=fields(body);
        var result=courses.update(actors.current(),id,new UpdateCourse(text(body,"title"),text(body,"description"),
                text(body,"priceAmount"),text(body,"currency"),text(body,"coverAssetId"),number(body,"coverPositionX"),number(body,"coverPositionY"),fields));
        return ResponseEntity.ok().header("Cache-Control","no-store").body(response(result));
    }
    @PostMapping("/api/coach/courses/{id}/archive")
    public CourseResponse archive(@PathVariable String id) { return response(courses.archive(actors.current(),id)); }
    private static Set<String> fields(JsonNode body) {
        if(body==null||!body.isObject())throw new BusinessProblem(400,"请填写课程信息");
        Set<String> result=new HashSet<>();body.propertyNames().forEach(result::add);return Set.copyOf(result);
    }
    private static String text(JsonNode body,String field) {
        JsonNode node=body.get(field);
        if(node==null||node.isNull())return null;
        if(!node.isString())throw new BusinessProblem(400,"课程字段格式错误");
        return node.asString();
    }
    private static BigDecimal number(JsonNode body,String field) {
        JsonNode node=body.get(field);
        if(node==null)return null;
        if(!node.isNumber())throw new BusinessProblem(400,"构图位置须为数字");
        try{return new BigDecimal(node.toString());}
        catch(RuntimeException error){throw new BusinessProblem(400,"构图位置须为有效数字");}
    }
}
