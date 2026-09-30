package com.geer.snowboard.v2.identity.adapter.out.persistence;

import com.geer.snowboard.v2.identity.application.port.out.PasswordResetMailQueue;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcPasswordResetMailQueue implements PasswordResetMailQueue {
    private final JdbcTemplate jdbc;
    public JdbcPasswordResetMailQueue(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional
    public Task claim(Instant now) {
        Long id = jdbc.query("""
                SELECT id FROM identity_password_reset_mail_task
                WHERE (status='PENDING' AND next_attempt_at<=?)
                   OR (status='CLAIMED' AND claim_until<=?)
                ORDER BY next_attempt_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, rs -> rs.next() ? rs.getLong(1) : null, now, now);
        if (id == null) return null;
        jdbc.update("""
                UPDATE identity_password_reset_mail_task
                SET status='CLAIMED',attempts=attempts+1,claim_until=? WHERE id=?
                """, now.plusSeconds(30), id);
        return jdbc.query("""
                SELECT t.id,r.id AS reset_id,a.email,t.attempts
                FROM identity_password_reset_mail_task t
                JOIN identity_password_reset r ON r.id=t.reset_id
                JOIN identity_account a ON a.id=r.account_id WHERE t.id=?
                """, rs -> rs.next() ? new Task(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4)) : null, id);
    }

    @Override public boolean isValid(long taskId, Instant now) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM identity_password_reset_mail_task t
                JOIN identity_password_reset r ON r.id=t.reset_id
                JOIN identity_account a ON a.id=r.account_id
                WHERE t.id=? AND t.status='CLAIMED' AND r.expires_at>?
                AND r.consumed_at IS NULL AND r.invalidated_at IS NULL
                AND r.wrong_attempts<5 AND a.verified_at IS NOT NULL)
                """, Boolean.class, taskId, now));
    }

    @Override public void sent(long id) {
        jdbc.update("""
                UPDATE identity_password_reset_mail_task SET status='SENT',claim_until=NULL,last_error=NULL
                WHERE id=? AND status='CLAIMED'
                """, id);
    }
    @Override public void failed(long id, Instant nextAttempt, String reason) {
        jdbc.update("""
                UPDATE identity_password_reset_mail_task
                SET status='PENDING',claim_until=NULL,next_attempt_at=?,last_error=?
                WHERE id=? AND status='CLAIMED'
                """, nextAttempt, reason, id);
    }
    @Override public void skipped(long id) {
        jdbc.update("""
                UPDATE identity_password_reset_mail_task SET status='SKIPPED',claim_until=NULL
                WHERE id=? AND status='CLAIMED'
                """, id);
    }
    @Override public void cleanup(Instant now) {
        Instant cutoff = now.minusSeconds(7 * 86400);
        jdbc.update("DELETE FROM identity_password_reset_mail_task WHERE status IN ('SENT','SKIPPED') AND next_attempt_at<?", cutoff);
        jdbc.update("""
                DELETE r FROM identity_password_reset r
                LEFT JOIN identity_password_reset_mail_task t ON t.reset_id=r.id
                WHERE r.created_at<? AND t.id IS NULL
                """, cutoff);
    }
}
