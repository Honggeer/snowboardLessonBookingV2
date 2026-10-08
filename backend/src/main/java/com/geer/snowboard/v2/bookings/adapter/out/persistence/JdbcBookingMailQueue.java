package com.geer.snowboard.v2.bookings.adapter.out.persistence;

import com.geer.snowboard.v2.bookings.application.port.out.BookingMailQueue;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcBookingMailQueue implements BookingMailQueue {
    private final JdbcTemplate jdbc;
    public JdbcBookingMailQueue(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void enqueue(String bookingId, String eventType, String recipientAccountId, Instant now) {
        jdbc.update("""
                INSERT INTO bookings_mail_task
                (booking_id,event_type,recipient_account_id,next_attempt_at,created_at)
                VALUES (?,?,?,?,?)
                """, bookingId,eventType,recipientAccountId,utc(now),utc(now));
    }
    @Override public void enqueueReminder(String bookingId, String eventType, String recipientAccountId,
                                          Instant dueAt, Instant startAt, Instant now) {
        jdbc.update("""
                INSERT INTO bookings_mail_task
                (booking_id,event_type,recipient_account_id,next_attempt_at,reminder_start_at_utc,created_at)
                VALUES (?,?,?,?,?,?)
                """, bookingId,eventType,recipientAccountId,utc(dueAt),utc(startAt),utc(now));
    }
    // Keep this entry transactional too: a default-method self-call bypasses the Spring proxy.
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public Task claim(Instant now) { return claim(now, true); }
    @Override @Transactional(isolation=Isolation.READ_COMMITTED)
    public Task claim(Instant now, boolean includeReminders) {
        jdbc.update("""
                UPDATE bookings_mail_task
                SET status='DEAD',claim_until=NULL,claim_token=NULL,last_error='Claim expired after final attempt'
                WHERE status='CLAIMED' AND claim_until<=? AND attempts>=8
                  AND (? OR event_type IN ('APPLICATION_RECEIVED','BOOKING_CONFIRMED','BOOKING_REJECTED'))
                """, utc(now),includeReminders);
        Long id=jdbc.query("""
                SELECT id FROM bookings_mail_task
                WHERE ((status='PENDING' AND next_attempt_at<=?)
                   OR (status='CLAIMED' AND claim_until<=? AND attempts<8))
                  AND (? OR event_type IN ('APPLICATION_RECEIVED','BOOKING_CONFIRMED','BOOKING_REJECTED'))
                ORDER BY next_attempt_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, rs->rs.next()?rs.getLong(1):null,utc(now),utc(now),includeReminders);
        if (id==null) return null;
        String token=UUID.randomUUID().toString();
        jdbc.update("""
                UPDATE bookings_mail_task SET status='CLAIMED',attempts=attempts+1,
                claim_until=CASE WHEN event_type IN ('STUDENT_LESSON_REMINDER','COACH_LESSON_REMINDER') THEN ? ELSE ? END,
                claim_token=? WHERE id=?
                """,utc(now.plusSeconds(120)),utc(now.plusSeconds(30)),token,id);
        return jdbc.query("""
                SELECT id,booking_id,event_type,recipient_account_id,attempts,claim_token,reminder_start_at_utc,claim_until
                FROM bookings_mail_task WHERE id=?
                """,rs->rs.next()?new Task(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4),
                rs.getInt(5),rs.getString(6),instant(rs,7),instant(rs,8)):null,id);
    }
    @Override public boolean ownsLease(Task task, Instant now) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM bookings_mail_task
                WHERE id=? AND status='CLAIMED' AND claim_token=? AND claim_until>?)
                """,Boolean.class,task.id(),task.claimToken(),utc(now)));
    }
    @Override public void sent(Task task, Instant now) {
        jdbc.update("""
                UPDATE bookings_mail_task
                SET status='SENT',claim_until=NULL,claim_token=NULL,last_error=NULL,sent_at=?
                WHERE id=? AND status='CLAIMED' AND claim_token=?
                """,utc(now),task.id(),task.claimToken());
    }
    @Override public void failed(Task task, Instant nextAttempt, String errorType, boolean finalFailure) {
        jdbc.update("""
                UPDATE bookings_mail_task
                SET status=?,claim_until=NULL,claim_token=NULL,next_attempt_at=?,last_error=?
                WHERE id=? AND status='CLAIMED' AND claim_token=?
                """,finalFailure?"DEAD":"PENDING",utc(nextAttempt),errorType,task.id(),task.claimToken());
    }
    @Override public void skipped(Task task) {
        jdbc.update("""
                UPDATE bookings_mail_task SET status='SKIPPED',claim_until=NULL,claim_token=NULL
                WHERE id=? AND status='CLAIMED' AND claim_token=?
                """,task.id(),task.claimToken());
    }
    @Override public void cleanup(Instant cutoff) {
        jdbc.update("DELETE FROM bookings_mail_task WHERE status IN ('SENT','SKIPPED') AND created_at<?",utc(cutoff));
    }
    private static LocalDateTime utc(Instant point) { return LocalDateTime.ofInstant(point,ZoneOffset.UTC); }
    private static Instant instant(ResultSet rs,int column) throws SQLException {
        LocalDateTime value=rs.getObject(column,LocalDateTime.class);
        return value==null?null:value.toInstant(ZoneOffset.UTC);
    }
}
