package com.geer.snowboard.v2.bookings.adapter.out.persistence;

import com.geer.snowboard.v2.bookings.application.port.out.BookingMailQueue;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcBookingMailQueue implements BookingMailQueue {
    private final JdbcTemplate jdbc;

    public JdbcBookingMailQueue(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void enqueue(String bookingId, String eventType, String recipientAccountId, Instant now) {
        jdbc.update("""
                INSERT INTO bookings_mail_task
                (booking_id,event_type,recipient_account_id,next_attempt_at,created_at)
                VALUES (?,?,?,?,?)
                """, bookingId, eventType, recipientAccountId, now, now);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Task claim(Instant now) {
        jdbc.update("""
                UPDATE bookings_mail_task
                SET status='DEAD',claim_until=NULL,claim_token=NULL,last_error='Claim expired after final attempt'
                WHERE status='CLAIMED' AND claim_until<=? AND attempts>=8
                """, now);
        Long id = jdbc.query("""
                SELECT id FROM bookings_mail_task
                WHERE (status='PENDING' AND next_attempt_at<=?)
                   OR (status='CLAIMED' AND claim_until<=? AND attempts<8)
                ORDER BY next_attempt_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, rs -> rs.next() ? rs.getLong(1) : null, now, now);
        if (id == null) return null;
        String token = UUID.randomUUID().toString();
        jdbc.update("""
                UPDATE bookings_mail_task
                SET status='CLAIMED',attempts=attempts+1,claim_until=?,claim_token=?
                WHERE id=?
                """, now.plusSeconds(30), token, id);
        return jdbc.query("""
                SELECT id,booking_id,event_type,recipient_account_id,attempts,claim_token
                FROM bookings_mail_task WHERE id=?
                """, rs -> rs.next() ? new Task(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getInt(5), rs.getString(6)) : null, id);
    }

    @Override
    public void sent(Task task, Instant now) {
        jdbc.update("""
                UPDATE bookings_mail_task
                SET status='SENT',claim_until=NULL,claim_token=NULL,last_error=NULL,sent_at=?
                WHERE id=? AND status='CLAIMED' AND claim_token=?
                """, now, task.id(), task.claimToken());
    }

    @Override
    public void failed(Task task, Instant nextAttempt, String errorType, boolean finalFailure) {
        jdbc.update("""
                UPDATE bookings_mail_task
                SET status=?,claim_until=NULL,claim_token=NULL,next_attempt_at=?,last_error=?
                WHERE id=? AND status='CLAIMED' AND claim_token=?
                """, finalFailure ? "DEAD" : "PENDING", nextAttempt, errorType, task.id(), task.claimToken());
    }

    @Override
    public void skipped(Task task) {
        jdbc.update("""
                UPDATE bookings_mail_task
                SET status='SKIPPED',claim_until=NULL,claim_token=NULL
                WHERE id=? AND status='CLAIMED' AND claim_token=?
                """, task.id(), task.claimToken());
    }

    @Override
    public void cleanup(Instant cutoff) {
        jdbc.update("DELETE FROM bookings_mail_task WHERE status IN ('SENT','SKIPPED') AND created_at<?", cutoff);
    }
}
