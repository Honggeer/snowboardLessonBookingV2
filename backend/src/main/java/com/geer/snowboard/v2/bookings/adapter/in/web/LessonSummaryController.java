package com.geer.snowboard.v2.bookings.adapter.in.web;

import com.geer.snowboard.v2.bookings.application.port.in.LessonSummaryOperations;
import com.geer.snowboard.v2.bookings.application.port.in.LessonSummaryOperations.CompletedLesson;
import com.geer.snowboard.v2.bookings.application.port.in.LessonSummaryOperations.Summary;
import com.geer.snowboard.v2.bootstrap.ActorResolver;
import com.geer.snowboard.v2.bootstrap.BookingLockRetry;
import com.geer.snowboard.v2.sharedkernel.Page;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public final class LessonSummaryController {
    private final LessonSummaryOperations summaries;
    private final ActorResolver actors;
    private final BookingLockRetry retry;
    public LessonSummaryController(LessonSummaryOperations summaries,ActorResolver actors,BookingLockRetry retry) {
        this.summaries=summaries; this.actors=actors; this.retry=retry;
    }
    record WriteRequest(String practiceContent,String improvementAreas,String reinforcementExercises,Long expectedVersion) {
        LessonSummaryOperations.Write command() {
            return new LessonSummaryOperations.Write(practiceContent,improvementAreas,reinforcementExercises,expectedVersion);
        }
    }
    record SummaryResponse(String bookingId,String state,String practiceContent,String improvementAreas,
                           String reinforcementExercises,long version,Instant updatedAt,Instant publishedAt) {
        static SummaryResponse from(Summary summary) {
            return new SummaryResponse(summary.bookingId(),summary.state(),summary.practiceContent(),summary.improvementAreas(),
                    summary.reinforcementExercises(),summary.version(),summary.updatedAt(),summary.publishedAt());
        }
    }
    record CompletedResponse(String id,String studentName,String courseTitle,String location,String zoneId,
                             LocalDate localDate,Instant startAt,Instant endAt,boolean summaryAvailable) {
        static CompletedResponse from(CompletedLesson lesson) {
            return new CompletedResponse(lesson.id(),lesson.studentName(),lesson.courseTitle(),lesson.location(),lesson.zoneId(),
                    lesson.localDate(),lesson.startAt(),lesson.endAt(),"PUBLISHED".equals(lesson.summaryState()));
        }
    }
    // Coach rows keep the same flat course fields as student rows, with an authorized summary state.
    record CoachLessonResponse(String id,String studentName,String courseTitle,String location,String zoneId,
                               LocalDate localDate,Instant startAt,Instant endAt,boolean summaryAvailable,String summaryState) {
        static CoachLessonResponse from(CompletedLesson lesson) {
            return new CoachLessonResponse(lesson.id(),lesson.studentName(),lesson.courseTitle(),lesson.location(),lesson.zoneId(),
                    lesson.localDate(),lesson.startAt(),lesson.endAt(),"PUBLISHED".equals(lesson.summaryState()),lesson.summaryState());
        }
    }
    @GetMapping("/api/bookings/completed")
    public ResponseEntity<Page<CompletedResponse>> completed(@RequestParam(required=false) Integer limit,@RequestParam(required=false) String cursor) {
        var page=summaries.completed(actors.current(),limit,cursor);
        return noStore(new Page<>(page.items().stream().map(CompletedResponse::from).toList(),page.nextCursor()));
    }
    @GetMapping("/api/coach/bookings/completed")
    public ResponseEntity<Page<CoachLessonResponse>> coachCompleted(@RequestParam(required=false) Integer limit,@RequestParam(required=false) String cursor) {
        var page=summaries.coachCompleted(actors.current(),limit,cursor);
        return noStore(new Page<>(page.items().stream().map(CoachLessonResponse::from).toList(),page.nextCursor()));
    }
    @GetMapping("/api/coach/bookings/{id}/summary")
    public ResponseEntity<SummaryResponse> coachSummary(@PathVariable String id) {
        return noStore(SummaryResponse.from(summaries.coachSummary(actors.current(),id)));
    }
    @GetMapping("/api/bookings/{id}/summary")
    public ResponseEntity<SummaryResponse> studentSummary(@PathVariable String id) {
        return noStore(SummaryResponse.from(summaries.studentSummary(actors.current(),id)));
    }
    @PatchMapping("/api/coach/bookings/{id}/summary")
    public ResponseEntity<SummaryResponse> save(@PathVariable String id,@RequestBody WriteRequest body) {
        var actor=actors.current();
        return noStore(SummaryResponse.from(retry.run(()->summaries.save(actor,id,body.command()))));
    }
    @PostMapping("/api/coach/bookings/{id}/summary/publish")
    public ResponseEntity<SummaryResponse> publish(@PathVariable String id,@RequestBody WriteRequest body) {
        var actor=actors.current();
        return noStore(SummaryResponse.from(retry.run(()->summaries.publish(actor,id,body.command()))));
    }
    private static <T> ResponseEntity<T> noStore(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
