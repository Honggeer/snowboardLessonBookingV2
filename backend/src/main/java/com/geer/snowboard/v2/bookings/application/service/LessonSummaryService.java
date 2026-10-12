package com.geer.snowboard.v2.bookings.application.service;

import com.geer.snowboard.v2.bookings.application.port.in.LessonSummaryOperations;
import com.geer.snowboard.v2.bookings.application.port.in.BookingOperations.Booking;
import com.geer.snowboard.v2.bookings.application.port.out.*;
import com.geer.snowboard.v2.bookings.domain.LessonSummary;
import com.geer.snowboard.v2.sharedkernel.*;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LessonSummaryService implements LessonSummaryOperations {
    private final BookingStore bookings;
    private final LessonSummaryStore summaries;
    private final BookingMailQueue queue;
    private final Clock clock;
    public LessonSummaryService(BookingStore bookings, LessonSummaryStore summaries, BookingMailQueue queue, Clock clock) {
        this.bookings=bookings; this.summaries=summaries; this.queue=queue; this.clock=clock;
    }
    @Override public Page<CompletedLesson> completed(Actor actor, Integer limit, String cursor) {
        actor.require("STUDENT");
        return summaries.completedForStudent(actor.id(),clock.instant(),RequestKeys.limit(limit),cursor);
    }
    @Override public Page<CompletedLesson> coachCompleted(Actor actor, Integer limit, String cursor) {
        actor.require("COACH");
        return summaries.completedForCoach(actor.id(),clock.instant(),RequestKeys.limit(limit),cursor);
    }
    @Override public Summary coachSummary(Actor actor, String bookingId) {
        actor.require("COACH");
        Booking booking=ownCoach(actor,bookingId,false);
        requireCompleted(booking);
        LessonSummary summary=summaries.find(bookingId);
        return result(summary==null?LessonSummary.empty(bookingId):summary);
    }
    @Override public Summary studentSummary(Actor actor, String bookingId) {
        actor.require("STUDENT");
        Booking booking=bookings.findById(bookingId);
        if(booking==null || !actor.id().equals(booking.studentId())) throw new BusinessProblem(404,"课程总结不存在");
        LessonSummary summary=summaries.find(bookingId);
        if(!LessonSummary.completed(booking.status(),booking.endAt(),clock.instant()) || summary==null
                || summary.state()!=LessonSummary.State.PUBLISHED) throw new BusinessProblem(404,"课程总结不存在");
        return result(summary);
    }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public Summary save(Actor actor,String bookingId,Write command) { return write(actor,bookingId,command,false); }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public Summary publish(Actor actor,String bookingId,Write command) { return write(actor,bookingId,command,true); }
    private Summary write(Actor actor,String bookingId,Write command,boolean publish) {
        actor.require("COACH");
        Booking booking=ownCoach(actor,bookingId,true);
        requireCompleted(booking);
        if(command==null || command.expectedVersion()==null || command.expectedVersion()<0)
            throw new BusinessProblem(400,"请提供有效的总结版本");
        var content=new LessonSummary.Content(command.practiceContent(),command.improvementAreas(),command.reinforcementExercises());
        LessonSummary old=summaries.find(bookingId);
        if(old==null) old=LessonSummary.empty(bookingId);
        var now=clock.instant();
        LessonSummary next=old.write(content,command.expectedVersion(),publish,now);
        if(next!=old) {
            summaries.save(next);
            if(old.publishedAt()==null && next.publishedAt()!=null) {
                bookings.markSummaryReminderPlanned(bookingId,now);
                queue.enqueue(bookingId,BookingMailQueue.STUDENT_SUMMARY_PUBLISHED,booking.studentId(),now);
            }
        }
        return result(next);
    }
    private Booking ownCoach(Actor actor,String bookingId,boolean lock) {
        Booking booking=lock?bookings.lockById(bookingId):bookings.findById(bookingId);
        if(booking==null || !actor.id().equals(booking.coachId())) throw new BusinessProblem(404,"课程不存在");
        return booking;
    }
    private void requireCompleted(Booking booking) {
        if(!LessonSummary.completed(booking.status(),booking.endAt(),clock.instant()))
            throw new BusinessProblem(409,"只能为已结束的确认课程填写总结");
    }
    private static Summary result(LessonSummary summary) {
        return new Summary(summary.bookingId(),summary.state().name(),summary.content().practiceContent(),
                summary.content().improvementAreas(),summary.content().reinforcementExercises(),summary.version(),summary.updatedAt(),summary.publishedAt());
    }
}
