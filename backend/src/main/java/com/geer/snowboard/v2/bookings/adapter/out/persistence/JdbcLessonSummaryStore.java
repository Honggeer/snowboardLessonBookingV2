package com.geer.snowboard.v2.bookings.adapter.out.persistence;

import com.geer.snowboard.v2.bookings.application.port.out.LessonSummaryStore;
import com.geer.snowboard.v2.bookings.application.port.in.LessonSummaryOperations.CompletedLesson;
import com.geer.snowboard.v2.bookings.domain.LessonSummary;
import com.geer.snowboard.v2.sharedkernel.BusinessProblem;
import com.geer.snowboard.v2.sharedkernel.Page;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLessonSummaryStore implements LessonSummaryStore {
    private final JdbcTemplate jdbc;
    public JdbcLessonSummaryStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public LessonSummary find(String bookingId) {
        return jdbc.query("SELECT * FROM bookings_lesson_summary WHERE booking_id=?",(rs,row)->{
            var published=rs.getObject("published_at",LocalDateTime.class);
            return new LessonSummary(rs.getString("booking_id"),LessonSummary.State.valueOf(rs.getString("state")),
                    new LessonSummary.Content(rs.getString("practice_content"),rs.getString("improvement_areas"),rs.getString("reinforcement_exercises")),
                    rs.getLong("version"),rs.getObject("updated_at",LocalDateTime.class).toInstant(ZoneOffset.UTC),
                    published==null?null:published.toInstant(ZoneOffset.UTC));
        },bookingId).stream().findFirst().orElse(null);
    }
    @Override public void save(LessonSummary summary) {
        var c=summary.content();
        if(summary.version()==1) {
            jdbc.update("""
                    INSERT INTO bookings_lesson_summary(booking_id,state,practice_content,improvement_areas,reinforcement_exercises,version,updated_at,published_at)
                    VALUES (?,?,?,?,?,?,?,?)
                    """,summary.bookingId(),summary.state().name(),c.practiceContent(),c.improvementAreas(),c.reinforcementExercises(),summary.version(),utc(summary.updatedAt()),utc(summary.publishedAt()));
        } else {
            int changed=jdbc.update("""
                    UPDATE bookings_lesson_summary SET state=?,practice_content=?,improvement_areas=?,reinforcement_exercises=?,version=?,updated_at=?,published_at=?
                    WHERE booking_id=? AND version=?
                    """,summary.state().name(),c.practiceContent(),c.improvementAreas(),c.reinforcementExercises(),summary.version(),utc(summary.updatedAt()),utc(summary.publishedAt()),summary.bookingId(),summary.version()-1);
            if(changed!=1) throw new BusinessProblem(409,"总结已更新，请读取最新版本");
        }
    }
    @Override public Page<CompletedLesson> completedForStudent(String studentId,Instant now,int limit,String cursor) {
        return page("student_id",studentId,now,limit,cursor);
    }
    @Override public Page<CompletedLesson> completedForCoach(String coachId,Instant now,int limit,String cursor) {
        return page("coach_id",coachId,now,limit,cursor);
    }
    private Page<CompletedLesson> page(String scope,String actorId,Instant now,int limit,String cursor) {
        String where="b."+scope+"=? AND b.status='CONFIRMED' AND b.end_at_utc_snapshot<=?";
        List<Object> args=new ArrayList<>(List.of(actorId,utc(now)));
        if(cursor!=null) {
            var previous=jdbc.query("SELECT end_at_utc_snapshot FROM bookings_request b WHERE b.id=? AND "+where,
                    (rs,row)->rs.getObject(1,LocalDateTime.class),cursor,actorId,utc(now));
            if(previous.isEmpty()) throw new BusinessProblem(400,"无效分页游标");
            where+=" AND (b.end_at_utc_snapshot<? OR (b.end_at_utc_snapshot=? AND b.id<?))";
            args.add(previous.getFirst()); args.add(previous.getFirst()); args.add(cursor);
        }
        args.add(limit+1);
        var rows=jdbc.query("""
                SELECT b.id,b.student_name_snapshot,b.course_title_snapshot,b.location_snapshot,b.zone_id_snapshot,
                    b.local_date_snapshot,b.start_at_utc_snapshot,b.end_at_utc_snapshot,COALESCE(s.state,'NOT_STARTED') AS summary_state
                FROM bookings_request b LEFT JOIN bookings_lesson_summary s ON s.booking_id=b.id WHERE
                """+where+" ORDER BY b.end_at_utc_snapshot DESC,b.id DESC LIMIT ?",(rs,row)->new CompletedLesson(
                rs.getString("id"),rs.getString("student_name_snapshot"),rs.getString("course_title_snapshot"),
                rs.getString("location_snapshot"),rs.getString("zone_id_snapshot"),rs.getObject("local_date_snapshot",LocalDate.class),
                rs.getObject("start_at_utc_snapshot",LocalDateTime.class).toInstant(ZoneOffset.UTC),
                rs.getObject("end_at_utc_snapshot",LocalDateTime.class).toInstant(ZoneOffset.UTC),rs.getString("summary_state")),args.toArray());
        return new Page<>(rows.subList(0,Math.min(limit,rows.size())),rows.size()>limit?rows.get(limit-1).id():null);
    }
    private static LocalDateTime utc(Instant instant) { return instant==null?null:LocalDateTime.ofInstant(instant,ZoneOffset.UTC); }
}
